package io.github.cctyl.nokia.keycore.model;

/**
 * 诺基亚按键机语义动作常量定义。
 */
public final class NokiaKeyAction {
    public static final int ACTION_UP = 0;
    public static final int ACTION_DOWN = 1;
    public static final int ACTION_LEFT = 2;
    public static final int ACTION_RIGHT = 3;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_SOFT_LEFT = 5;
    public static final int ACTION_SOFT_RIGHT = 6;
    public static final int ACTION_LOCK_SCREEN = 7;
    public static final int ACTION_CALL = 8;
    public static final int ACTION_UNKNOWN = -1;

    public static final String[] ACTION_NAMES = {
            "上", "下", "左", "右", "确定", "左软键", "右软键", "锁屏", "拨号"
    };

    public static final String[] ACTION_KEYS = {
            "UP", "DOWN", "LEFT", "RIGHT", "SELECT", "SOFT_LEFT", "SOFT_RIGHT", "LOCK_SCREEN", "CALL"
    };

    private NokiaKeyAction() {}

    public static String getActionName(int action) {
        if (action >= 0 && action < ACTION_NAMES.length) {
            return ACTION_NAMES[action];
        }
        return "未知";
    }

    public static int parseActionKey(String key) {
        if (key == null) return ACTION_UNKNOWN;
        for (int i = 0; i < ACTION_KEYS.length; i++) {
            if (ACTION_KEYS[i].equalsIgnoreCase(key)) {
                return i;
            }
        }
        return ACTION_UNKNOWN;
    }
}
