package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 诺基亚复古点阵字体管理器。
 * 负责全局字体加载、字体回退与 View 树智能递归应用。
 */
public class NokiaFontManager {

    public static final String FONT_ID_ARK_12PX = "ark_pixel_12px";
    public static final String FONT_ID_FUSION_12PX = "fusion_pixel_12px";
    public static final String FONT_ID_SYSTEM_DEFAULT = "system_default";

    private static final String PATH_ARK_12PX = "fonts/ArkPixel-12px.ttf";
    private static final String PATH_FUSION_12PX = "fonts/FusionPixel-12px.ttf";

    private static final ConcurrentHashMap<String, Typeface> sCache = new ConcurrentHashMap<>();
    private static String sCurrentFontId = FONT_ID_ARK_12PX;
    private static float sFontScale = 1.0f;

    public static synchronized void setCurrentFontId(String fontId) {
        if (fontId != null) {
            if ("ark_12px".equals(fontId)) fontId = FONT_ID_ARK_12PX;
            else if ("fusion_12px".equals(fontId)) fontId = FONT_ID_FUSION_12PX;
        }
        sCurrentFontId = (fontId != null) ? fontId : FONT_ID_ARK_12PX;
    }

    public static synchronized String getCurrentFontId() {
        return sCurrentFontId;
    }

    public static synchronized void setFontScale(float scale) {
        if (scale > 0.1f && scale < 5.0f) {
            sFontScale = scale;
        }
    }

    public static synchronized float getFontScale() {
        return sFontScale;
    }

    public static Typeface getTypeface(Context context) {
        return getTypeface(context, sCurrentFontId);
    }

    public static Typeface getTypeface(Context context, String fontId) {
        if (FONT_ID_SYSTEM_DEFAULT.equals(fontId) || context == null) {
            return Typeface.DEFAULT;
        }

        Typeface cached = sCache.get(fontId);
        if (cached != null) {
            return cached;
        }

        String path = FONT_ID_FUSION_12PX.equals(fontId) ? PATH_FUSION_12PX : PATH_ARK_12PX;
        try {
            Typeface tf = Typeface.createFromAsset(context.getAssets(), path);
            sCache.put(fontId, tf);
            return tf;
        } catch (Exception e) {
            // 如果 assets 中没有内置该中文字体文件，回退到默认
            return Typeface.DEFAULT;
        }
    }

    public static void applyToViewTree(View root) {
        if (root == null) return;
        Typeface tf = getTypeface(root.getContext());
        applyTypefaceRecursively(root, tf, sFontScale);
    }

    private static final int TAG_ORIGINAL_TEXT_SIZE = 0x7f099999;

    private static void applyTypefaceRecursively(View view, Typeface tf, float scale) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            // 保护 Material Icons 字体：如果当前 TextView 使用的是 MaterialIcons 字体，则跳过
            Typeface current = tv.getTypeface();
            Typeface iconTf = NokiaIcons.getTypeface(tv.getContext());
            if (current != null && iconTf != null && current.equals(iconTf)) {
                return;
            }
            tv.setTypeface(tf);

            // 字体大小缩放
            if (scale > 0.1f && Math.abs(scale - 1.0f) > 0.01f) {
                Object tag = tv.getTag(TAG_ORIGINAL_TEXT_SIZE);
                float originalPx;
                if (tag instanceof Float) {
                    originalPx = (Float) tag;
                } else {
                    originalPx = tv.getTextSize(); // 获取原始 px 大小
                    tv.setTag(TAG_ORIGINAL_TEXT_SIZE, originalPx);
                }
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalPx * scale);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTypefaceRecursively(vg.getChildAt(i), tf, scale);
            }
        }
    }

    public static void invalidate() {
        sCache.clear();
    }
}
