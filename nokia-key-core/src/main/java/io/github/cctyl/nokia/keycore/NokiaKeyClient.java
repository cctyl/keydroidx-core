package io.github.cctyl.nokia.keycore;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 诺基亚按键客户端（单例）。
 * 负责与诺基亚桌面 ContentProvider 通信并提供统一按键解析与变动监听。
 */
public class NokiaKeyClient {

    private static final String TAG = "NokiaKeyClient";

    // 桌面 ContentProvider Authorities（先查正式版，再查开发版）
    private static final String AUTH_RELEASE = "io.github.cctyl.nokia.keyprovider";
    private static final String AUTH_DEBUG = "io.github.cctyl.nokia.debug.keyprovider";

    private static volatile NokiaKeyClient sInstance;

    private final Context appContext;
    private final NokiaKeyBinding keyBinding = new NokiaKeyBinding();
    private boolean isFromDesktop = false;
    private Uri connectedUri = null;

    public interface OnKeyBindingChangedListener {
        void onKeyBindingChanged(NokiaKeyBinding binding, boolean fromDesktop);
    }

    private final List<OnKeyBindingChangedListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver contentObserver;

    public static NokiaKeyClient get(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (NokiaKeyClient.class) {
                if (sInstance == null) {
                    sInstance = new NokiaKeyClient(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private NokiaKeyClient(Context context) {
        this.appContext = context;
        reload();
        setupObserver();
    }

    /**
     * 重新加载按键配置（遵循：桌面优先 -> 本地配置 -> 默认按键）。
     */
    public synchronized void reload() {
        // 1. 尝试从诺基亚桌面 ContentProvider 读取
        if (tryLoadFromLauncher(AUTH_RELEASE) || tryLoadFromLauncher(AUTH_DEBUG)) {
            isFromDesktop = true;
            Log.i(TAG, "成功从诺基亚桌面同步按键配置: " + connectedUri);
            notifyListeners();
            return;
        }

        // 2. 未装桌面，尝试加载本地独立配置
        isFromDesktop = false;
        connectedUri = null;
        if (NokiaKeyBinding.hasConfiguredLocally(appContext)) {
            keyBinding.loadFromLocal(appContext);
            Log.i(TAG, "未检测到桌面，加载本应用本地保存的按键配置");
        } else {
            keyBinding.resetToDefaults();
            Log.i(TAG, "未检测到桌面及本地配置，加载标准 Android 兜底按键");
        }
        notifyListeners();
    }

    private boolean tryLoadFromLauncher(String authority) {
        Uri uri = Uri.parse("content://" + authority + "/keys");
        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                keyBinding.resetToDefaults();
                int colAction = cursor.getColumnIndex("action");
                int colActionId = cursor.getColumnIndex("actionId");
                int colKeyCode = cursor.getColumnIndex("keyCode");

                while (cursor.moveToNext()) {
                    int actionId = -1;
                    if (colActionId != -1) {
                        actionId = cursor.getInt(colActionId);
                    } else if (colAction != -1) {
                        String actionStr = cursor.getString(colAction);
                        actionId = NokiaKeyAction.parseActionKey(actionStr);
                    }
                    int keyCode = colKeyCode != -1 ? cursor.getInt(colKeyCode) : 0;

                    if (actionId >= 0 && keyCode > 0) {
                        keyBinding.bind(actionId, keyCode);
                    }
                }
                connectedUri = uri;
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "尝试查询 " + authority + " 失败: ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private void setupObserver() {
        if (connectedUri == null) return;
        contentObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, @Nullable Uri uri) {
                super.onChange(selfChange, uri);
                Log.i(TAG, "收到桌面按键变动通知，自动重新同步...");
                reload();
            }
        };
        try {
            appContext.getContentResolver().registerContentObserver(connectedUri, false, contentObserver);
        } catch (Exception ignored) {}
    }

    public synchronized void destroy() {
        try {
            if (contentObserver != null) {
                appContext.getContentResolver().unregisterContentObserver(contentObserver);
            }
        } catch (Exception ignored) {}
    }

    public synchronized NokiaKeyBinding getBinding() {
        return keyBinding;
    }

    public synchronized boolean isFromDesktop() {
        return isFromDesktop;
    }

    public synchronized void registerListener(OnKeyBindingChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void unregisterListener(OnKeyBindingChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                List<OnKeyBindingChangedListener> copy;
                synchronized (NokiaKeyClient.this) {
                    copy = new ArrayList<>(listeners);
                }
                for (OnKeyBindingChangedListener l : copy) {
                    l.onKeyBindingChanged(keyBinding, isFromDesktop);
                }
            }
        });
    }
}
