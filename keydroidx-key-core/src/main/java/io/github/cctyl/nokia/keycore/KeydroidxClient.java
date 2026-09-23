package io.github.cctyl.nokia.keycore;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import io.github.cctyl.nokia.common.log.KeydroidxLog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.github.cctyl.nokia.common.contract.KeydroidxProviderContract;
import io.github.cctyl.nokia.common.ui.KeydroidxTheme;
import io.github.cctyl.nokia.common.ui.ThemeProvider;
import io.github.cctyl.nokia.common.model.KeydroidxKeyAction;
import io.github.cctyl.nokia.keycore.model.KeydroidxKeyBinding;
import io.github.cctyl.nokia.common.ui.KeydroidxFontManager;

/**
 * KeydroidX 统一生态客户端。
 * 负责按键、主题、字体等全局配置的跨进程读取、四级平滑降级与热同步监听。
 */
public class KeydroidxClient implements ThemeProvider {

    private static final String TAG = "KeydroidxClient";

    public static final String RELEASE_AUTHORITY = KeydroidxProviderContract.AUTHORITY_RELEASE;
    public static final String DEBUG_AUTHORITY = KeydroidxProviderContract.AUTHORITY_DEBUG;

    private static final String PREF_NAME = "nokia_client_prefs";
    private static final String KEY_THEME_ID = KeydroidxProviderContract.SETTING_THEME_ID;
    private static final String KEY_FONT_ID = KeydroidxProviderContract.SETTING_FONT_ID;
    private static final String KEY_FONT_SCALE = KeydroidxProviderContract.SETTING_FONT_SCALE;

    public enum ConfigSource {
        DESKTOP_RELEASE,
        DESKTOP_DEBUG,
        LOCAL_CUSTOM,
        FALLBACK_DEFAULT
    }

    public interface OnConfigChangedListener {
        void onKeysChanged(@NonNull KeydroidxKeyBinding binding, @NonNull ConfigSource source);
        void onThemeChanged(@NonNull String themeId, @NonNull KeydroidxTheme.ThemeDef theme);
        void onFontChanged(@NonNull String fontId, float fontScale);
    }

    private static volatile KeydroidxClient sInstance;

    private final Context context;
    private final KeydroidxKeyBinding keyBinding;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<OnConfigChangedListener> listeners = new CopyOnWriteArrayList<>();

    private ConfigSource configSource = ConfigSource.FALLBACK_DEFAULT;
    private String currentThemeId = KeydroidxTheme.THEME_CLASSIC_BLUE;
    private String currentFontId = KeydroidxFontManager.FONT_ID_ARK_12PX;
    private float currentFontScale = 1.0f;
    private ContentObserver contentObserver;

    /**
     * 桌面 Provider 同步专用后台线程。
     * <p>跨进程 {@code ContentResolver.query} 是 Binder 同步阻塞调用：桌面进程未启动时
     * 本地端要等 AMS 把桌面进程冷启动起来，低端机上可达数秒；放在主线程会使首次启动
     * 窗口迟迟不出（Input dispatching timed out ANR）。因此所有 Provider 探测一律在此线程执行。
     */
    private final ExecutorService providerExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "keydroidx-desktop-sync");
            t.setDaemon(true);
            return t;
        }
    });

    private KeydroidxClient(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.keyBinding = new KeydroidxKeyBinding();
        this.mainHandler = new Handler(Looper.getMainLooper());
        KeydroidxTheme.setThemeProvider(this);
        loadLocalPrefs();
        // 主线程只用本地缓存建立可用状态（零跨进程等待），保证首帧立即可渲染
        applyLocalConfig();
        // 桌面 Provider 同步（可能冷启动桌面进程）放后台线程，查完回主线程热更新
        reloadAsync();
    }

    /** 应用第 3/4 级降级结果：本地独立配置 → 标准默认键码。仅在主线程调用。 */
    private void applyLocalConfig() {
        if (keyBinding.loadFromLocal(context)) {
            configSource = ConfigSource.LOCAL_CUSTOM;
        } else {
            keyBinding.initDefaults();
            configSource = ConfigSource.FALLBACK_DEFAULT;
        }
        dispatchConfigChanged();
    }

    public static KeydroidxClient get(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (KeydroidxClient.class) {
                if (sInstance == null) {
                    sInstance = new KeydroidxClient(context);
                }
            }
        }
        return sInstance;
    }

    private void loadLocalPrefs() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentThemeId = sp.getString(KEY_THEME_ID, KeydroidxTheme.THEME_CLASSIC_BLUE);
        this.currentFontId = sp.getString(KEY_FONT_ID, KeydroidxFontManager.FONT_ID_ARK_12PX);
        this.currentFontScale = sp.getFloat(KEY_FONT_SCALE, 1.0f);
        KeydroidxFontManager.setCurrentFontId(this.currentFontId);
        KeydroidxFontManager.setFontScale(this.currentFontScale);
        KeydroidxLog.i(TAG, "loadLocalPrefs: theme=" + currentThemeId + " font=" + currentFontId + " scale=" + currentFontScale);
    }

    private void saveLocalPrefs() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_ID, currentThemeId)
                .putString(KEY_FONT_ID, currentFontId)
                .putFloat(KEY_FONT_SCALE, currentFontScale)
                .apply();
    }

    private boolean isDebugLauncherPreferred() {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolveInfo = pm.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                String pkg = resolveInfo.activityInfo.packageName;
                if (pkg != null && (pkg.endsWith(".debug") || pkg.contains("debug"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            KeydroidxLog.w(TAG, "resolve home activity failed, use default provider order: " + ignored.getMessage());
        }
        return false;
    }

    /**
     * 同步执行四级降级重载。
     * <p><b>⚠ 主线程禁止调用</b>：内部会跨进程查询桌面 Provider（Binder 阻塞调用，桌面进程
     * 未启动时需等 AMS 冷启动桌面，低端机可达数秒）。主线程请改用 {@link #reloadAsync()}。
     */
    public synchronized void reload() {
        reloadInternal();
    }

    /** 异步执行四级降级重载：Provider 查询与解析在后台线程完成，结果回主线程生效。主线程调用安全。 */
    public void reloadAsync() {
        providerExecutor.execute(new Runnable() {
            @Override
            public void run() {
                reload();
            }
        });
    }

    private void reloadInternal() {
        boolean preferDebug = isDebugLauncherPreferred();
        ProviderSnapshot snapshot;
        if (preferDebug) {
            snapshot = queryProvider(DEBUG_AUTHORITY, ConfigSource.DESKTOP_DEBUG);
            if (snapshot == null) {
                snapshot = queryProvider(RELEASE_AUTHORITY, ConfigSource.DESKTOP_RELEASE);
            }
        } else {
            snapshot = queryProvider(RELEASE_AUTHORITY, ConfigSource.DESKTOP_RELEASE);
            if (snapshot == null) {
                snapshot = queryProvider(DEBUG_AUTHORITY, ConfigSource.DESKTOP_DEBUG);
            }
        }

        if (snapshot != null) {
            final ProviderSnapshot result = snapshot;
            KeydroidxLog.i(TAG, "reload: 命中桌面 Provider " + result.authority + " source=" + result.source);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    applySnapshot(result);
                }
            });
            return;
        }

        // 3. 降级：本地独立配置；4. 降级：标准默认配置
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                applyLocalConfig();
                KeydroidxLog.i(TAG, "reload: 未命中桌面 Provider，降级本地配置 source=" + configSource
                        + " theme=" + currentThemeId);
            }
        });
    }

    /**
     * 纯查询：只读桌面 Provider 并产出不可变快照，<b>不触碰任何成员状态</b>，可安全在后台线程执行。
     *
     * @return 命中并读到按键表时返回快照；未安装/无权限/无数据时返回 null（触发下一级降级）
     */
    @Nullable
    private ProviderSnapshot queryProvider(@NonNull String authority, @NonNull ConfigSource source) {
        try {
            ProviderSnapshot snapshot = new ProviderSnapshot(authority, source);
            boolean keysLoaded = false;

            // 查询按键: content://{authority}/keys
            Uri keysUri = KeydroidxProviderContract.getKeysUri(authority);
            Cursor cursor = context.getContentResolver().query(keysUri, null, null, null, null);
            if (cursor != null) {
                try {
                    int actionIdx = cursor.getColumnIndex(KeydroidxProviderContract.COL_ACTION);
                    int keyCodeIdx = cursor.getColumnIndex(KeydroidxProviderContract.COL_KEY_CODE);
                    if (cursor.moveToFirst()) {
                        do {
                            String actionStr = (actionIdx >= 0) ? cursor.getString(actionIdx) : null;
                            int keyCode = (keyCodeIdx >= 0) ? cursor.getInt(keyCodeIdx) : -1;
                            int action = KeydroidxKeyAction.parseActionKey(actionStr);
                            if (action >= 0 && keyCode > 0) {
                                snapshot.bindings.add(new int[]{action, keyCode});
                            }
                        } while (cursor.moveToNext());
                        keysLoaded = true;
                    }
                } finally {
                    cursor.close();
                }
            }

            // 查询主题与设置: content://{authority}/settings
            Uri settingsUri = KeydroidxProviderContract.getSettingsUri(authority);
            Cursor sCursor = context.getContentResolver().query(settingsUri, null, null, null, null);
            if (sCursor != null) {
                try {
                    int keyIdx = sCursor.getColumnIndex(KeydroidxProviderContract.COL_KEY);
                    int valIdx = sCursor.getColumnIndex(KeydroidxProviderContract.COL_VALUE);
                    if (sCursor.moveToFirst()) {
                        do {
                            String k = (keyIdx >= 0) ? sCursor.getString(keyIdx) : null;
                            String v = (valIdx >= 0) ? sCursor.getString(valIdx) : null;
                            if (KeydroidxProviderContract.SETTING_THEME_ID.equals(k) && v != null) {
                                snapshot.themeId = v;
                            } else if (KeydroidxProviderContract.SETTING_FONT_ID.equals(k) && v != null) {
                                snapshot.fontId = v;
                            } else if (KeydroidxProviderContract.SETTING_FONT_SCALE.equals(k) && v != null) {
                                try {
                                    snapshot.fontScale = Float.parseFloat(v);
                                } catch (Exception ignored) {
                                    KeydroidxLog.w(TAG, "parse font scale failed, value=" + v + ": " + ignored.getMessage());
                                }
                            }
                        } while (sCursor.moveToNext());
                    }
                } finally {
                    sCursor.close();
                }
            }

            if (keysLoaded) {
                return snapshot;
            }
        } catch (SecurityException e) {
            KeydroidxLog.w(TAG, "Package visibility 或权限受限无法查询: " + authority, e);
        } catch (Exception e) {
            KeydroidxLog.e(TAG, "查询 Provider 异常: " + authority, e);
        }
        return null;
    }

    /** 把后台线程查到的桌面快照应用为当前生效配置（含观察者注册与全局回调）。仅在主线程调用。 */
    private void applySnapshot(@NonNull ProviderSnapshot snapshot) {
        keyBinding.clear();
        for (int[] pair : snapshot.bindings) {
            keyBinding.bind(pair[0], pair[1]);
        }
        if (snapshot.themeId != null) {
            this.currentThemeId = snapshot.themeId;
        }
        if (snapshot.fontId != null) {
            this.currentFontId = snapshot.fontId;
            KeydroidxFontManager.setCurrentFontId(snapshot.fontId);
        }
        if (snapshot.fontScale != null) {
            this.currentFontScale = snapshot.fontScale;
            KeydroidxFontManager.setFontScale(snapshot.fontScale);
        }
        configSource = snapshot.source;
        saveLocalPrefs();

        Uri keysUri = KeydroidxProviderContract.getKeysUri(snapshot.authority);
        registerObserver(keysUri);
        registerObserver(KeydroidxProviderContract.getSettingsUri(snapshot.authority));
        dispatchConfigChanged();
    }

    /** 桌面前后台数据传递载体：后台线程只写它，主线程读它，避免跨线程直接改共享状态。 */
    private static final class ProviderSnapshot {
        final String authority;
        final ConfigSource source;
        final List<int[]> bindings = new ArrayList<>();
        String themeId;
        String fontId;
        Float fontScale;

        ProviderSnapshot(@NonNull String authority, @NonNull ConfigSource source) {
            this.authority = authority;
            this.source = source;
        }
    }

    private void registerObserver(Uri uri) {
        if (contentObserver == null) {
            contentObserver = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    KeydroidxLog.i(TAG, "收到桌面配置变更通知，自动重新加载");
                    // 主线程回调，必须走异步重载（同步 query 会阻塞主线程）
                    reloadAsync();
                }
            };
            try {
                context.getContentResolver().registerContentObserver(uri, true, contentObserver);
            } catch (Exception e) {
                KeydroidxLog.w(TAG, "注册 ContentObserver 失败", e);
            }
        }
    }

    private void dispatchConfigChanged() {
        final KeydroidxKeyBinding bindingCopy = keyBinding.clone();
        final ConfigSource src = configSource;
        final String tId = currentThemeId;
        final KeydroidxTheme.ThemeDef theme = KeydroidxTheme.getTheme(tId);
        final String fId = currentFontId;
        final float fScale = currentFontScale;

        mainHandler.post(() -> {
            for (OnConfigChangedListener l : listeners) {
                l.onKeysChanged(bindingCopy, src);
                l.onThemeChanged(tId, theme);
                l.onFontChanged(fId, fScale);
            }
        });
    }

    public KeydroidxKeyBinding getBinding() {
        return keyBinding;
    }

    public KeydroidxKeyBinding getKeyBinding() {
        return keyBinding;
    }

    public ConfigSource getConfigSource() {
        return configSource;
    }

    public boolean isFromDesktop() {
        return configSource == ConfigSource.DESKTOP_RELEASE || configSource == ConfigSource.DESKTOP_DEBUG;
    }

    public String getCurrentThemeId() {
        return currentThemeId;
    }

    public KeydroidxTheme.ThemeDef getCurrentTheme() {
        return KeydroidxTheme.getTheme(currentThemeId);
    }

    @Override
    public KeydroidxTheme.ThemeDef getCurrentTheme(Context ctx) {
        return getCurrentTheme();
    }

    public String getCurrentFontId() {
        return currentFontId;
    }

    public float getCurrentFontScale() {
        return currentFontScale;
    }

    public void setThemeId(String themeId) {
        this.currentThemeId = themeId;
        saveLocalPrefs();
        dispatchConfigChanged();
    }

    public void setFontId(String fontId) {
        this.currentFontId = fontId;
        KeydroidxFontManager.setCurrentFontId(fontId);
        saveLocalPrefs();
        dispatchConfigChanged();
    }

    public void addListener(OnConfigChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            // 立即回调当前最新值
            listener.onKeysChanged(keyBinding.clone(), configSource);
            listener.onThemeChanged(currentThemeId, getCurrentTheme());
            listener.onFontChanged(currentFontId, currentFontScale);
        }
    }

    public void removeListener(OnConfigChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void registerListener(OnConfigChangedListener listener) {
        addListener(listener);
    }

    public void unregisterListener(OnConfigChangedListener listener) {
        removeListener(listener);
    }
}
