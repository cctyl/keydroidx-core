package io.github.cctyl.nokia.keycore;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;
import io.github.cctyl.nokia.keycore.ui.NokiaFontManager;
import io.github.cctyl.nokia.keycore.ui.NokiaTheme;

/**
 * KeydroidX 统一生态客户端。
 * 负责按键、主题、字体等全局配置的跨进程读取、三级降级与热同步监听。
 */
public class NokiaClient {

    private static final String TAG = "NokiaClient";

    public static final String RELEASE_AUTHORITY = "io.github.cctyl.nokia.keyprovider";
    public static final String DEBUG_AUTHORITY = "io.github.cctyl.nokia.debug.keyprovider";

    private static final String PREF_NAME = "nokia_client_prefs";
    private static final String KEY_THEME_ID = "theme_id";
    private static final String KEY_FONT_ID = "font_id";
    private static final String KEY_FONT_SCALE = "font_scale";

    public enum ConfigSource {
        DESKTOP_RELEASE,
        DESKTOP_DEBUG,
        LOCAL_CUSTOM,
        FALLBACK_DEFAULT
    }

    public interface OnConfigChangedListener {
        void onKeysChanged(@NonNull NokiaKeyBinding binding, @NonNull ConfigSource source);
        void onThemeChanged(@NonNull String themeId, @NonNull NokiaTheme.ThemeDef theme);
        void onFontChanged(@NonNull String fontId, float fontScale);
    }

    private static volatile NokiaClient sInstance;

    private final Context context;
    private final NokiaKeyBinding keyBinding;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<OnConfigChangedListener> listeners = new CopyOnWriteArrayList<>();

    private ConfigSource configSource = ConfigSource.FALLBACK_DEFAULT;
    private String currentThemeId = NokiaTheme.THEME_CLASSIC_BLUE;
    private String currentFontId = NokiaFontManager.FONT_ID_ARK_12PX;
    private float currentFontScale = 1.0f;
    private ContentObserver contentObserver;

    private NokiaClient(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.keyBinding = new NokiaKeyBinding();
        this.mainHandler = new Handler(Looper.getMainLooper());
        loadLocalPrefs();
        reload();
    }

    public static NokiaClient get(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (NokiaClient.class) {
                if (sInstance == null) {
                    sInstance = new NokiaClient(context);
                }
            }
        }
        return sInstance;
    }

    private void loadLocalPrefs() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentThemeId = sp.getString(KEY_THEME_ID, NokiaTheme.THEME_CLASSIC_BLUE);
        this.currentFontId = sp.getString(KEY_FONT_ID, NokiaFontManager.FONT_ID_ARK_12PX);
        this.currentFontScale = sp.getFloat(KEY_FONT_SCALE, 1.0f);
        NokiaFontManager.setCurrentFontId(this.currentFontId);
        NokiaFontManager.setFontScale(this.currentFontScale);
    }

    private void saveLocalPrefs() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_ID, currentThemeId)
                .putString(KEY_FONT_ID, currentFontId)
                .putFloat(KEY_FONT_SCALE, currentFontScale)
                .apply();
    }

    public synchronized void reload() {
        // 1. 查询 Release 桌面 Provider
        if (tryQueryProvider(RELEASE_AUTHORITY, ConfigSource.DESKTOP_RELEASE)) {
            return;
        }
        // 2. 查询 Debug 桌面 Provider
        if (tryQueryProvider(DEBUG_AUTHORITY, ConfigSource.DESKTOP_DEBUG)) {
            return;
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
    }

    private boolean tryQueryProvider(String authority, ConfigSource source) {
        boolean keysLoaded = false;
        try {
            // 查询按键: content://{authority}/keys
            Uri keysUri = Uri.parse("content://" + authority + "/keys");
            Cursor cursor = context.getContentResolver().query(keysUri, null, null, null, null);
            if (cursor != null) {
                try {
                    int actionIdx = cursor.getColumnIndex("action");
                    int keyCodeIdx = cursor.getColumnIndex("keyCode");
                    if (cursor.moveToFirst()) {
                        keyBinding.clear();
                        do {
                            String actionStr = (actionIdx >= 0) ? cursor.getString(actionIdx) : null;
                            int keyCode = (keyCodeIdx >= 0) ? cursor.getInt(keyCodeIdx) : -1;
                            int action = NokiaKeyAction.parseActionKey(actionStr);
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
            Uri settingsUri = Uri.parse("content://" + authority + "/settings");
            Cursor sCursor = context.getContentResolver().query(settingsUri, null, null, null, null);
            if (sCursor != null) {
                try {
                    int keyIdx = sCursor.getColumnIndex("key");
                    int valIdx = sCursor.getColumnIndex("value");
                    if (sCursor.moveToFirst()) {
                        do {
                            String k = (keyIdx >= 0) ? sCursor.getString(keyIdx) : null;
                            String v = (valIdx >= 0) ? sCursor.getString(valIdx) : null;
                            if ("theme_id".equals(k) && v != null) {
                                this.currentThemeId = v;
                            } else if ("font_id".equals(k) && v != null) {
                                this.currentFontId = v;
                                NokiaFontManager.setCurrentFontId(v);
                            } else if ("font_scale".equals(k) && v != null) {
                                try {
                                    this.currentFontScale = Float.parseFloat(v);
                                    NokiaFontManager.setFontScale(this.currentFontScale);
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
                dispatchConfigChanged();
                return true;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Package visibility 或权限受限无法查询: " + authority, e);
        } catch (Exception e) {
            Log.e(TAG, "查询 Provider 异常: " + authority, e);
        }
        return false;
    }

    private void registerObserver(Uri uri) {
        if (contentObserver == null) {
            contentObserver = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    Log.i(TAG, "收到桌面配置变更通知，自动重新加载");
                    reload();
                }
            };
            try {
                context.getContentResolver().registerContentObserver(uri, true, contentObserver);
            } catch (Exception e) {
                Log.w(TAG, "注册 ContentObserver 失败", e);
            }
        }
    }

    private void dispatchConfigChanged() {
        final NokiaKeyBinding bindingCopy = keyBinding.clone();
        final ConfigSource src = configSource;
        final String tId = currentThemeId;
        final NokiaTheme.ThemeDef theme = NokiaTheme.getTheme(tId);
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

    public NokiaKeyBinding getBinding() {
        return keyBinding;
    }

    public NokiaKeyBinding getKeyBinding() {
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

    public NokiaTheme.ThemeDef getCurrentTheme() {
        return NokiaTheme.getTheme(currentThemeId);
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
        NokiaFontManager.setCurrentFontId(fontId);
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
