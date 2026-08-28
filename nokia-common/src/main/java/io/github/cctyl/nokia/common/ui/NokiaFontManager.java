package io.github.cctyl.nokia.common.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 诺基亚复古点阵字体管理器。
 * <p>
 * 负责全局字体加载、字体回退与 View 树智能递归应用。
 * <p>
 * 属于 {@code nokia-common} 基础层，纯渲染工具，零业务/进程依赖，
 * 桌面 Launcher 与独立 App（经 nokia-key-core）共享同一份实现。
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

    private static final int TAG_HIERARCHY_WATCHER_ATTACHED = 0x7f099998;

    /**
     * 遍历并应用字体和字号缩放。
     * 默认开启自动监听（自动劫持子 View 动态变更），无需宿主在每次 addView / removeAllViews 后重复调用。
     */
    public static void applyToViewTree(View root) {
        applyToViewTree(root, true);
    }

    /**
     * 遍历并应用字体和字号缩放。
     * @param root 根 View
     * @param autoAttachWatcher 是否自动为 ViewGroup 挂载动态监听器（自动劫持后续动态 addView）
     */
    public static void applyToViewTree(View root, boolean autoAttachWatcher) {
        if (root == null) return;
        Typeface tf = getTypeface(root.getContext());
        applyTypefaceRecursively(root, tf, sFontScale, autoAttachWatcher);
    }

    /**
     * 全局动态监听：为指定的 ViewGroup 树挂载 OnHierarchyChangeListener，
     * 任何后续动态 addView 或 inflate 的子节点都会在挂载瞬间自动应用字体与缩放。
     */
    public static void attachAutoHierarchyWatcher(View root) {
        if (root == null) return;
        Typeface tf = getTypeface(root.getContext());
        attachHierarchyWatcherRecursively(root, tf);
    }

    private static final int TAG_ORIGINAL_TEXT_SIZE = 0x7f099999;

    /**
     * 以 SP 为单位设置设计字号，按当前字体倍率缩放并应用。
     * @param tv 目标 TextView
     * @param spSize 设计字号（SP）
     */
    public static void setTextSize(TextView tv, float spSize) {
        setTextSize(tv, android.util.TypedValue.COMPLEX_UNIT_SP, spSize);
    }

    /**
     * 从 dimens 资源读取尺寸并作为设计字号应用。
     * @param tv 目标 TextView
     * @param dimenResId 尺寸资源 ID（如 R.dimen.nokia_font_body）
     */
    public static void setTextSizeResource(TextView tv, int dimenResId) {
        if (tv == null || tv.getResources() == null) return;
        float designPx = tv.getResources().getDimension(dimenResId);
        setTextSize(tv, android.util.TypedValue.COMPLEX_UNIT_PX, designPx);
    }

    /**
     * 以「未缩放的设计字号」设置文字大小，按当前桌面缩放基准立即生效。
     * 供动态创建的 TextView 使用，保证与 XML 静态文本同一套缩放语义：
     * 实际字号 = 设计字号 × fontScale，且设计值只记录一次、可重复应用不漂移。
     */
    public static void setTextSize(TextView tv, int unit, float size) {
        if (tv == null) return;
        float designPx;
        switch (unit) {
            case android.util.TypedValue.COMPLEX_UNIT_SP:
            case android.util.TypedValue.COMPLEX_UNIT_DIP:
                designPx = android.util.TypedValue.applyDimension(
                        unit, size, tv.getResources().getDisplayMetrics());
                break;
            default:
                designPx = size;
                break;
        }
        tv.setTag(TAG_ORIGINAL_TEXT_SIZE, designPx);
        applyScaledTextSize(tv);
    }

    /** 按已记录的设计字号 × 当前缩放应用实际字号（无记录则以当前字号为设计值） */
    private static void applyScaledTextSize(TextView tv) {
        Object tag = tv.getTag(TAG_ORIGINAL_TEXT_SIZE);
        float designPx;
        if (tag instanceof Float) {
            designPx = (Float) tag;
        } else {
            designPx = tv.getTextSize(); // 首次遇到：XML 布局在缩放前填充，当前值即设计值
            tv.setTag(TAG_ORIGINAL_TEXT_SIZE, designPx);
        }
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, designPx * sFontScale);
    }

    private static void applyTypefaceRecursively(View view, Typeface tf, float scale, boolean autoAttachWatcher) {
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

            // 字体大小缩放：始终按 设计字号×scale 应用。
            // 不能因 scale≈1 而跳过——否则从放大状态切回 100% 时旧字号无法还原；
            // 也不能直接乘当前字号——否则动态创建的 View 会被重复放大（有的字大有的字小）。
            applyScaledTextSize(tv);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            if (autoAttachWatcher) {
                attachHierarchyWatcherInternal(vg, tf);
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTypefaceRecursively(vg.getChildAt(i), tf, scale, autoAttachWatcher);
            }
        }
    }

    private static void attachHierarchyWatcherRecursively(View view, Typeface tf) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            attachHierarchyWatcherInternal(vg, tf);
            for (int i = 0; i < vg.getChildCount(); i++) {
                attachHierarchyWatcherRecursively(vg.getChildAt(i), tf);
            }
        }
    }

    private static void attachHierarchyWatcherInternal(ViewGroup vg, Typeface tf) {
        if (vg == null) return;
        Object tag = vg.getTag(TAG_HIERARCHY_WATCHER_ATTACHED);
        if (Boolean.TRUE.equals(tag)) {
            return;
        }
        vg.setTag(TAG_HIERARCHY_WATCHER_ATTACHED, Boolean.TRUE);

        vg.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                if (child != null) {
                    applyTypefaceRecursively(child, tf, sFontScale, true);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
    }

    public static void invalidate() {
        sCache.clear();
    }
}
