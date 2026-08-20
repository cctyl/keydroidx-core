package io.github.cctyl.nokia.keycore.model;

/**
 * 诺基亚按键机语义动作常量定义。
 */
public final class NokiaKeyAction {
    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int SELECT = 4;
    public static final int SOFT_LEFT = 5;
    public static final int SOFT_RIGHT = 6;
    public static final int LOCK_SCREEN = 7;
    public static final int CALL = 8;
    public static final int UNKNOWN = -1;

    // 兼容别名
    public static final int ACTION_UP = UP;
    public static final int ACTION_DOWN = DOWN;
    public static final int ACTION_LEFT = LEFT;
    public static final int ACTION_RIGHT = RIGHT;
    public static final int ACTION_SELECT = SELECT;
    public static final int ACTION_SOFT_LEFT = SOFT_LEFT;
    public static final int ACTION_SOFT_RIGHT = SOFT_RIGHT;
    public static final int ACTION_LOCK_SCREEN = LOCK_SCREEN;
    public static final int ACTION_CALL = CALL;
    public static final int ACTION_UNKNOWN = UNKNOWN;

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
