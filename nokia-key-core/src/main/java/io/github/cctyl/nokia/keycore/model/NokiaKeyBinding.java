package io.github.cctyl.nokia.keycore.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * 诺基亚物理按键映射模型与持久化管理
 */
public class NokiaKeyBinding {

    private static final String PREF_NAME = "nokia_key_bindings";

    public static final int ACTION_COUNT = 9;

    public static final String[] ACTION_PROMPTS = {
            "上",
            "下",
            "左",
            "右",
            "确定",
            "左软键",
            "右软键",
            "锁屏",
            "拨号"
    };

    private final SparseIntArray actionToKeyCode = new SparseIntArray();
    private final SparseIntArray keyCodeToAction = new SparseIntArray();

    public NokiaKeyBinding() {
        initDefaults();
    }

    public NokiaKeyBinding(@NonNull Context context) {
        initDefaults();
        loadLocal(context);
    }

    public synchronized void resetToDefaults() {
        initDefaults();
    }

    private void initDefaults() {
        actionToKeyCode.clear();
        keyCodeToAction.clear();
        bind(NokiaKeyAction.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP);
        bind(NokiaKeyAction.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        bind(NokiaKeyAction.ACTION_LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
        bind(NokiaKeyAction.ACTION_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
        bind(NokiaKeyAction.ACTION_SELECT, KeyEvent.KEYCODE_DPAD_CENTER);
        bind(NokiaKeyAction.ACTION_SOFT_LEFT, KeyEvent.KEYCODE_MENU);
        bind(NokiaKeyAction.ACTION_SOFT_RIGHT, KeyEvent.KEYCODE_BACK);
        bind(NokiaKeyAction.ACTION_LOCK_SCREEN, KeyEvent.KEYCODE_ENDCALL);
        bind(NokiaKeyAction.ACTION_CALL, KeyEvent.KEYCODE_CALL);
    }

    public synchronized void bind(int action, int keyCode) {
        if (keyCode <= 0) return;
        actionToKeyCode.put(action, keyCode);
        keyCodeToAction.put(keyCode, action);
    }

    public synchronized void setKeyCode(int action, int keyCode) {
        bind(action, keyCode);
    }

    public synchronized int getKeyCode(int action) {
        return actionToKeyCode.get(action, -1);
    }

    public synchronized int resolveAction(int keyCode) {
        return keyCodeToAction.get(keyCode, -1);
    }

    public synchronized int resolveAction(KeyEvent event) {
        if (event == null) return -1;
        int kc = event.getKeyCode();
        int action = resolveAction(kc);
        if (action >= 0) return action;

        // 默认 Enter 键兜底为确定
        if (kc == KeyEvent.KEYCODE_ENTER && actionToKeyCode.indexOfKey(NokiaKeyAction.ACTION_SELECT) < 0) {
            return NokiaKeyAction.ACTION_SELECT;
        }
        return -1;
    }

    public static String getWizardPromptName(int step) {
        if (step >= 0 && step < ACTION_PROMPTS.length) {
            return ACTION_PROMPTS[step];
        }
        return "未知";
    }

    public static boolean hasConfiguredLocally(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.contains(NokiaKeyAction.ACTION_KEYS[0]);
    }

    public synchronized void save(@NonNull Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        for (int i = 0; i < NokiaKeyAction.ACTION_KEYS.length; i++) {
            editor.putInt(NokiaKeyAction.ACTION_KEYS[i], getKeyCode(i));
        }
        editor.apply();
    }

    public synchronized void loadLocal(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        for (int i = 0; i < NokiaKeyAction.ACTION_KEYS.length; i++) {
            int kc = sp.getInt(NokiaKeyAction.ACTION_KEYS[i], -1);
            if (kc > 0) {
                bind(i, kc);
            }
        }
    }

    public synchronized void loadFromLocal(@NonNull Context context) {
        loadLocal(context);
    }

    public synchronized NokiaKeyBinding clone() {
        NokiaKeyBinding copy = new NokiaKeyBinding();
        for (int i = 0; i < NokiaKeyAction.ACTION_KEYS.length; i++) {
            copy.bind(i, this.getKeyCode(i));
        }
        return copy;
    }
}
