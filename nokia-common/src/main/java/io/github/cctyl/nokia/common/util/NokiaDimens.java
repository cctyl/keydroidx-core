package io.github.cctyl.nokia.common.util;

import android.content.res.Resources;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * 诺基亚复古像素尺寸与单位换算工具。
 */
public final class NokiaDimens {

    private NokiaDimens() {
        // utility class
    }

    /**
     * 将 dp 值转换为像素整数值（截断），保持像素网格对齐。
     */
    public static int dp(Resources res, int dpValue) {
        if (res == null) return dpValue;
        return (int) (dpValue * res.getDisplayMetrics().density);
    }

    /**
     * 将 float dp 值转换为像素整数值（截断）。
     */
    public static int dp(Resources res, float dpValue) {
        if (res == null) return (int) dpValue;
        return (int) (dpValue * res.getDisplayMetrics().density);
    }

    /**
     * 将 dp 值转换为像素浮点值。
     */
    public static float dpF(Resources res, float dpValue) {
        if (res == null) return dpValue;
        return dpValue * res.getDisplayMetrics().density;
    }

    /**
     * 为 TextView 设置基于像素单位的字号（避免系统缩放破坏复古排版）。
     */
    public static void textSize(TextView tv, float sizeDp) {
        if (tv != null && tv.getResources() != null) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(tv.getResources(), sizeDp));
        }
    }
}
