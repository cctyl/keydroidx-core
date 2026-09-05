package io.github.cctyl.nokia.common.ui;

import io.github.cctyl.nokia.common.log.NokiaLog;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诺基亚复古点阵字体管理器（生态唯一实现）。
 * <p>
 * 负责全局字体加载/缓存/分发与字号缩放，是 KeydroidX 生态字体体系的<strong>单一事实源</strong>。
 * 规范见 {@code docs/11-typography-and-font-spec.md}。
 * <p>
 * 缩放链路：桌面【设置→字体大小】→ {@code font_scale} → 本类 {@link #setFontScale(float)} →
 * {@link #applyToViewTree(android.view.View)} 遍历时乘以 {@code sFontScale}。
 * <b>严禁</b>在 {@code attachBaseContext} 修改 {@code Configuration.fontScale}（双重缩放陷阱）。
 * <p>
 * 本类同时承载原 Launcher 本地 {@code NokiaFontManager} 的全部能力（自定义外部字体导入、
 * {@code textSize}、{@code applyFontToViewHierarchy}、{@code FontItem} 等），以便桌面与衍生 App
 * 共用同一份实现，不再有第二套静态缩放字段。
 */
public class NokiaFontManager {

    private static final String TAG = "NokiaFontManager";

    public static final String FONT_ID_ARK_12PX = "ark_pixel_12px";
    public static final String FONT_ID_FUSION_12PX = "fusion_pixel_12px";
    public static final String FONT_ID_SYSTEM_DEFAULT = "system_default";
    /** 别名：兼容原 Launcher 本地常量名 */
    public static final String FONT_ID_SYSTEM = FONT_ID_SYSTEM_DEFAULT;
    /** 自定义外部字体前缀（fontId = custom_ + 文件名） */
    public static final String FONT_ID_CUSTOM_PREFIX = "custom_";

    private static final String PATH_ARK_12PX = "fonts/ArkPixel-12px.ttf";
    private static final String PATH_FUSION_12PX = "fonts/FusionPixel-12px.ttf";

    private static final ConcurrentHashMap<String, Typeface> sCache = new ConcurrentHashMap<>();
    private static String sCurrentFontId = FONT_ID_ARK_12PX;
    private static float sFontScale = 1.0f;

    // ====================================================================
    // 缩放倍率（生态唯一缩放源）
    // ====================================================================

    public static synchronized void setFontScale(float scale) {
        if (scale > 0.1f && scale < 5.0f) {
            sFontScale = scale;
        }
    }

    public static synchronized float getFontScale() {
        return sFontScale;
    }

    /**
     * 兼容旧 API：与 {@link #setFontScale} 等价。
     * 原 Launcher 本地 NokiaFontManager.setUserFontScale 的同义入口，保留以减少迁移面。
     */
    public static void setUserFontScale(float scale) {
        setFontScale(scale);
    }

    // ====================================================================
    // 当前字体 ID
    // ====================================================================

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

    // ====================================================================
    // Typeface 加载（内置 + 自定义外部字体）
    // ====================================================================

    public static Typeface getTypeface(Context context) {
        return getTypeface(context, sCurrentFontId);
    }

    public static Typeface getTypeface(Context context, String fontId) {
        if (fontId == null || FONT_ID_SYSTEM_DEFAULT.equals(fontId) || context == null) {
            return Typeface.DEFAULT;
        }
        Typeface cached = sCache.get(fontId);
        if (cached != null) return cached;
        Typeface tf = loadTypeface(context, fontId);
        return tf != null ? tf : Typeface.DEFAULT;
    }

    /**
     * 获取当前全局生效的 Typeface。原 Launcher 本地 getGlobalTypeface，现统一走 {@link #sCurrentFontId}。
     * 注意：系统默认字体返回 {@link Typeface#DEFAULT}（原本地实现返回 null，调用方 Font.java 以
     * 非 null 判定使用全局字体，DEFAULT 与其 fallback 等价，行为一致）。
     */
    public static Typeface getGlobalTypeface(Context context) {
        return getTypeface(context);
    }

    /**
     * 根据 fontId 加载 Typeface（内置 assets 或应用私有目录下的自定义字体文件）。
     * 成功则缓存。失败返回 null（由调用方回退到系统默认）。
     */
    public static Typeface loadTypeface(Context context, String fontId) {
        if (fontId == null || FONT_ID_SYSTEM_DEFAULT.equals(fontId)) return Typeface.DEFAULT;
        Typeface cached = sCache.get(fontId);
        if (cached != null) return cached;

        Typeface tf = null;
        try {
            if (FONT_ID_ARK_12PX.equals(fontId)) {
                tf = Typeface.createFromAsset(context.getAssets(), PATH_ARK_12PX);
            } else if (FONT_ID_FUSION_12PX.equals(fontId)) {
                tf = Typeface.createFromAsset(context.getAssets(), PATH_FUSION_12PX);
            } else if (fontId.startsWith(FONT_ID_CUSTOM_PREFIX)) {
                File fontDir = new File(context.getFilesDir(), "fonts");
                File fontFile = new File(fontDir, fontId.substring(FONT_ID_CUSTOM_PREFIX.length()));
                if (fontFile.exists() && fontFile.canRead()) {
                    tf = Typeface.createFromFile(fontFile);
                }
            }
        } catch (Throwable t) {
            NokiaLog.e(TAG, "加载字体失败: " + fontId, t);
        }

        if (tf != null) {
            sCache.put(fontId, tf);
        }
        return tf;
    }

    // ====================================================================
    // 自定义外部字体管理（原 Launcher 本地能力）
    // ====================================================================

    /** 可用字体条目。 */
    public static class FontItem {
        public final String id;
        public final String name;
        public final String description;
        public final boolean isCustom;

        public FontItem(String id, String name, String description, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isCustom = isCustom;
        }
    }

    /** 获取可用字体列表（内置 + 自定义）。 */
    public static List<FontItem> getAvailableFonts(Context context) {
        List<FontItem> list = new ArrayList<>();
        list.add(new FontItem(FONT_ID_ARK_12PX, "方舟像素体 (12px 经典)", "经典 12 点阵像素字体，紧凑精致", false));
        list.add(new FontItem(FONT_ID_FUSION_12PX, "缝合怪像素体 (12px 全字符)", "CJK 全字符无死角覆盖，大字符集推荐", false));
        list.add(new FontItem(FONT_ID_SYSTEM, "系统默认字体", "系统原生无衬线字体 (Roboto / 默认)", false));

        File fontDir = new File(context.getFilesDir(), "fonts");
        if (fontDir.exists() && fontDir.isDirectory()) {
            File[] files = fontDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && (f.getName().endsWith(".ttf") || f.getName().endsWith(".otf"))) {
                        list.add(new FontItem(FONT_ID_CUSTOM_PREFIX + f.getName(), f.getName(),
                                "自定义导入字体文件 (" + (f.length() / 1024) + " KB)", true));
                    }
                }
            }
        }
        return list;
    }

    /**
     * 从 Uri 导入外部字体文件并存入应用私有目录。
     * @return 导入后的 fontId，失败返回 null
     */
    public static String importFontFromUri(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try {
            String fileName = null;
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            if (fileName == null) {
                fileName = "font_" + System.currentTimeMillis() + ".ttf";
            }

            File fontDir = new File(context.getFilesDir(), "fonts");
            if (!fontDir.exists()) {
                fontDir.mkdirs();
            }

            File destFile = new File(fontDir, fileName);
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            Typeface tf = Typeface.createFromFile(destFile);
            if (tf == null) {
                destFile.delete();
                return null;
            }
            return FONT_ID_CUSTOM_PREFIX + fileName;
        } catch (Throwable t) {
            NokiaLog.e(TAG, "导入字体文件失败: " + uri, t);
            return null;
        }
    }

    /** 删除自定义字体。 */
    public static boolean deleteCustomFont(Context context, String fontId) {
        if (context == null || fontId == null || !fontId.startsWith(FONT_ID_CUSTOM_PREFIX)) {
            return false;
        }
        File fontDir = new File(context.getFilesDir(), "fonts");
        File fontFile = new File(fontDir, fontId.substring(FONT_ID_CUSTOM_PREFIX.length()));
        boolean ok = fontFile.delete();
        if (ok) {
            sCache.remove(fontId);
        }
        return ok;
    }

    /** 清除内存缓存，触发重新加载。 */
    public static void invalidate() {
        sCache.clear();
    }

    // ====================================================================
    // View 树应用：字体 + 字号缩放（统一入口）
    // ====================================================================

    private static final int TAG_HIERARCHY_WATCHER_ATTACHED = 0x7f099998;

    /**
     * 遍历并应用字体和字号缩放，默认开启自动监听（自动劫持后续动态 addView）。
     * 这是生态统一的 View 树应用入口：既换点阵字体，又乘以 {@code sFontScale} 缩放字号。
     */
    public static void applyToViewTree(View root) {
        applyToViewTree(root, true);
    }

    public static void applyToViewTree(View root, boolean autoAttachWatcher) {
        if (root == null) return;
        Typeface tf = getTypeface(root.getContext());
        applyTypefaceRecursively(root, tf, sFontScale, autoAttachWatcher);
    }

    /**
     * 全局动态监听：为指定 ViewGroup 树挂载 OnHierarchyChangeListener，
     * 后续动态 addView / inflate 的子节点在挂载瞬间自动应用字体与缩放。
     */
    public static void attachAutoHierarchyWatcher(View root) {
        if (root == null) return;
        Typeface tf = getTypeface(root.getContext());
        attachHierarchyWatcherRecursively(root, tf);
    }

    /**
     * 兼容旧 API：与 {@link #applyToViewTree(View)} 等价。
     * 原 Launcher 本地 applyFontToViewHierarchy 仅替换 Typeface 不缩字号（导致缩放不一致的 bug 源），
     * 现统一收口为"换字体 + 乘 sFontScale 缩字号"，与 Music/common Fragment 走同一套逻辑。
     */
    public static void applyFontToViewHierarchy(View root) {
        applyToViewTree(root);
    }

    /**
     * 兼容旧 API：以指定 Typeface 应用（含字号缩放）。
     */
    public static void applyFontToViewHierarchy(View root, Typeface tf) {
        if (root == null) return;
        applyTypefaceRecursively(root, tf != null ? tf : getTypeface(root.getContext()),
                sFontScale, true);
    }

    private static final int TAG_ORIGINAL_TEXT_SIZE = 0x7f099999;

    /**
     * 以 SP 为单位设置设计字号，按当前字体倍率缩放并应用。
     */
    public static void setTextSize(TextView tv, float spSize) {
        setTextSize(tv, TypedValue.COMPLEX_UNIT_SP, spSize);
    }

    /**
     * 从 dimens 资源读取尺寸并作为设计字号应用。
     * @param dimenResId 尺寸资源 ID（如 R.dimen.nokia_font_body）
     */
    public static void setTextSizeResource(TextView tv, int dimenResId) {
        if (tv == null || tv.getResources() == null) return;
        float designPx = tv.getResources().getDimension(dimenResId);
        setTextSize(tv, TypedValue.COMPLEX_UNIT_PX, designPx);
    }

    /**
     * 以「未缩放的设计字号」设置文字大小，按当前桌面缩放基准立即生效。
     * 实际字号 = 设计字号 × sFontScale，设计值只记录一次、可重复应用不漂移。
     */
    public static void setTextSize(TextView tv, int unit, float size) {
        if (tv == null) return;
        float designPx;
        switch (unit) {
            case TypedValue.COMPLEX_UNIT_SP:
            case TypedValue.COMPLEX_UNIT_DIP:
                designPx = TypedValue.applyDimension(
                        unit, size, tv.getResources().getDisplayMetrics());
                break;
            default:
                designPx = size;
                break;
        }
        tv.setTag(TAG_ORIGINAL_TEXT_SIZE, designPx);
        applyScaledTextSize(tv);
    }

    /**
     * 兼容旧 API：以 dp 为单位设置设计字号，并同步应用全局点阵字体。
     * 原 Launcher 本地 textSize(TextView, float dpValue) 的同义入口：
     * 内部走 {@link #setTextSize(TextView, int, float)} 以 DIP 单位计算设计 px，
     * 再乘以统一 {@code sFontScale}，结果与原实现（dp × density × userFontScale）一致。
     * 同时为该 TextView 应用当前全局字体（带 MaterialIcons 保护），避免动态创建的文字漏字体。
     */
    public static void textSize(TextView tv, float dpValue) {
        if (tv == null) return;
        setTextSize(tv, TypedValue.COMPLEX_UNIT_DIP, dpValue);
        applyTypefaceToSingleView(tv);
    }

    /** 按已记录的设计字号 × 当前缩放应用实际字号（无记录则以当前字号为设计值） */
    private static void applyScaledTextSize(TextView tv) {
        Object tag = tv.getTag(TAG_ORIGINAL_TEXT_SIZE);
        float designPx;
        if (tag instanceof Float) {
            designPx = (Float) tag;
        } else {
            designPx = tv.getTextSize(); // 首次遇到：XML 在缩放前填充，当前值即设计值
            tv.setTag(TAG_ORIGINAL_TEXT_SIZE, designPx);
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, designPx * sFontScale);
    }

    /** 对单个 TextView 应用全局字体（含 MaterialIcons 保护），不递归、不挂监听。 */
    private static void applyTypefaceToSingleView(TextView tv) {
        if (tv == null) return;
        Typeface curTf = tv.getTypeface();
        Typeface iconTf = NokiaIcons.getTypeface(tv.getContext());
        if (curTf != null && iconTf != null && curTf.equals(iconTf)) {
            return;
        }
        Typeface tf = getTypeface(tv.getContext());
        tv.setTypeface(tf != null ? tf : Typeface.DEFAULT);
    }

    private static void applyTypefaceRecursively(View view, Typeface tf, float scale, boolean autoAttachWatcher) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            // 保护 Material Icons 字体：当前 TextView 用的是 MaterialIcons 则跳过
            Typeface current = tv.getTypeface();
            Typeface iconTf = NokiaIcons.getTypeface(tv.getContext());
            if (current != null && iconTf != null && current.equals(iconTf)) {
                return;
            }
            tv.setTypeface(tf);
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
}
