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

import java.util.concurrent.CopyOnWriteArrayList;

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

    private KeydroidxClient(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.keyBinding = new KeydroidxKeyBinding();
        this.mainHandler = new Handler(Looper.getMainLooper());
        KeydroidxTheme.setThemeProvider(this);
        loadLocalPrefs();
        reload();
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
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized void reload() {
        boolean preferDebug = isDebugLauncherPreferred();
        if (preferDebug) {
            if (tryQueryProvider(DEBUG_AUTHORITY, ConfigSource.DESKTOP_DEBUG)) {
                KeydroidxLog.i(TAG, "reload: from debug desktop, theme=" + currentThemeId + " source=" + configSource);
                saveLocalPrefs();
                return;
            }
            if (tryQueryProvider(RELEASE_AUTHORITY, ConfigSource.DESKTOP_RELEASE)) {
                KeydroidxLog.i(TAG, "reload: from release desktop, theme=" + currentThemeId + " source=" + configSource);
                saveLocalPrefs();
                return;
            }
        } else {
            if (tryQueryProvider(RELEASE_AUTHORITY, ConfigSource.DESKTOP_RELEASE)) {
                KeydroidxLog.i(TAG, "reload: from release desktop, theme=" + currentThemeId + " source=" + configSource);
                saveLocalPrefs();
                return;
            }
            if (tryQueryProvider(DEBUG_AUTHORITY, ConfigSource.DESKTOP_DEBUG)) {
                KeydroidxLog.i(TAG, "reload: from debug desktop, theme=" + currentThemeId + " source=" + configSource);
                saveLocalPrefs();
                return;
            }
        }
        // 3. 降级：本地独立配置
        if (keyBinding.loadFromLocal(context)) {
            configSource = ConfigSource.LOCAL_CUSTOM;
            dispatchConfigChanged();
            return;
        }
        // 4. 降级：标准默认配置
        keyBinding.initDefaults();
        configSource = ConfigSource.FALLBACK_DEFAULT;
        dispatchConfigChanged();
        KeydroidxLog.i(TAG, "reload: final theme=" + currentThemeId + " source=" + configSource);
    }

    private boolean tryQueryProvider(String authority, ConfigSource source) {
        boolean keysLoaded = false;
        try {
            // 查询按键: content://{authority}/keys
            Uri keysUri = KeydroidxProviderContract.getKeysUri(authority);
            Cursor cursor = context.getContentResolver().query(keysUri, null, null, null, null);
            if (cursor != null) {
                try {
                    int actionIdx = cursor.getColumnIndex(KeydroidxProviderContract.COL_ACTION);
                    int keyCodeIdx = cursor.getColumnIndex(KeydroidxProviderContract.COL_KEY_CODE);
                    if (cursor.moveToFirst()) {
                        keyBinding.clear();
                        do {
                            String actionStr = (actionIdx >= 0) ? cursor.getString(actionIdx) : null;
                            int keyCode = (keyCodeIdx >= 0) ? cursor.getInt(keyCodeIdx) : -1;
                            int action = KeydroidxKeyAction.parseActionKey(actionStr);
                            if (action >= 0 && keyCode > 0) {
                                keyBinding.bind(action, keyCode);
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
                                this.currentThemeId = v;
                            } else if (KeydroidxProviderContract.SETTING_FONT_ID.equals(k) && v != null) {
                                this.currentFontId = v;
                                KeydroidxFontManager.setCurrentFontId(v);
                            } else if (KeydroidxProviderContract.SETTING_FONT_SCALE.equals(k) && v != null) {
                                try {
                                    this.currentFontScale = Float.parseFloat(v);
                                    KeydroidxFontManager.setFontScale(this.currentFontScale);
                                } catch (Exception ignored) {}
                            }
                        } while (sCursor.moveToNext());
                    }
                } finally {
                    sCursor.close();
                }
            }

            if (keysLoaded) {
                configSource = source;
                registerObserver(keysUri);
                registerObserver(settingsUri);
                dispatchConfigChanged();
                return true;
            }
        } catch (SecurityException e) {
            KeydroidxLog.w(TAG, "Package visibility 或权限受限无法查询: " + authority, e);
        } catch (Exception e) {
            KeydroidxLog.e(TAG, "查询 Provider 异常: " + authority, e);
        }
        return false;
    }

    private void registerObserver(Uri uri) {
        if (contentObserver == null) {
            contentObserver = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    KeydroidxLog.i(TAG, "收到桌面配置变更通知，自动重新加载");
                    reload();
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
