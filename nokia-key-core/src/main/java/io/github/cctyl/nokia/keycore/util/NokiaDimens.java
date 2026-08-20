package io.github.cctyl.nokia.keycore.util;

import android.content.res.Resources;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * 像素/尺寸转换工具（规避 Resources.getSystem() 的 DPI 偏差 Bug）
 */
public final class NokiaDimens {

    private NokiaDimens() {}

    public static int dp(Resources res, float dpValue) {
        return Math.round(dpF(res, dpValue));
    }

    public static float dpF(Resources res, float dpValue) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dpValue,
                res.getDisplayMetrics()
        );
    }

    public static void textSize(TextView tv, float spOrDpValue) {
        if (tv != null) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, spOrDpValue);
        }
    }
}
