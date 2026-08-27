package io.github.cctyl.nokia.common.contract;

import android.net.Uri;
import androidx.annotation.NonNull;

/**
 * KeydroidX 生态 ContentProvider 通信契约常量。
 * 统一定义桌面宿主（Provider）与生态各接入端（Client）之间的 URI、列名及设置键名。
 */
public final class NokiaProviderContract {

    private NokiaProviderContract() {}

    /** 正式版桌面 Provider Authority */
    public static final String AUTHORITY_RELEASE = "io.github.cctyl.nokia.keyprovider";

    /** 开发/Debug 版桌面 Provider Authority */
    public static final String AUTHORITY_DEBUG = "io.github.cctyl.nokia.debug.keyprovider";

    /** 按键映射数据表路径 */
    public static final String PATH_KEYS = "keys";

    /** 全局外观与参数设置数据表路径 */
    public static final String PATH_SETTINGS = "settings";

    /** 统一 MIME 类型 */
    public static final String MIME_TYPE = "vnd.android.cursor.dir/vnd.nokia.ecosystem";

    // ─────────────────────────────────────────────────────────────
    //  /keys 表列定义 (NokiaKeyAction 映射)
    // ─────────────────────────────────────────────────────────────

    /** 动作语义标签（如 "UP", "DOWN", "SOFT_LEFT" 等） */
    public static final String COL_ACTION = "action";

    /** 动作数字 ID（0..8） */
    public static final String COL_ACTION_ID = "actionId";

    /** 绑定的物理键码 KeyCode（如 KeyEvent.KEYCODE_DPAD_UP） */
    public static final String COL_KEY_CODE = "keyCode";

    /** 物理按键的人类可读名称（如 "DPAD_UP", "F1", "CALL" 等） */
    public static final String COL_KEY_NAME = "keyName";

    // ─────────────────────────────────────────────────────────────
    //  /settings 表列定义 (K-V 配置)
    // ─────────────────────────────────────────────────────────────

    /** 配置项 Key 列名 */
    public static final String COL_KEY = "key";

    /** 配置项 Value 列名 */
    public static final String COL_VALUE = "value";

    /** 设置项：当前全局主题 ID（如 "classic_blue", "pure_black" 等） */
    public static final String SETTING_THEME_ID = "theme_id";

    /** 设置项：当前全局点阵字体 ID（如 "fusion_12px", "ark_12px" 等） */
    public static final String SETTING_FONT_ID = "font_id";

    /** 设置项：当前全局字体缩放比例（浮点数字符串，如 "1.0", "1.2" 等） */
    public static final String SETTING_FONT_SCALE = "font_scale";

    // ─────────────────────────────────────────────────────────────
    //  Uri 构建辅助方法
    // ─────────────────────────────────────────────────────────────

    @NonNull
    public static Uri getKeysUri(@NonNull String authority) {
        return Uri.parse("content://" + authority + "/" + PATH_KEYS);
    }

    @NonNull
    public static Uri getSettingsUri(@NonNull String authority) {
        return Uri.parse("content://" + authority + "/" + PATH_SETTINGS);
    }
}
