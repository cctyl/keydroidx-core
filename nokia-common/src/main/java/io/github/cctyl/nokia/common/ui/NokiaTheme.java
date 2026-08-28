package io.github.cctyl.nokia.common.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.NonNull;

/**
 * 诺基亚复古主题系统核心定义与调色板。
 * <p>
 * 提供 6 种预设主题调色板与像素渐变 Drawable 构建工具。
 * 通过 {@link ThemeProvider} 接口解耦当前主题获取逻辑，使 SDK 与桌面共享同一套主题数据结构。
 */
public class NokiaTheme {

    public static final String THEME_CLASSIC_BLUE = "classic_blue";
    public static final String THEME_EMERALD_GREEN = "emerald_green";
    public static final String THEME_AMBER_GOLD = "amber_gold";
    public static final String THEME_OBSIDIAN_BLACK = "obsidian_black";
    public static final String THEME_CYAN_SEA = "cyan_sea";
    public static final String THEME_WINE_PURPLE = "wine_purple";

    public static final ThemeDef CLASSIC_BLUE = getTheme(THEME_CLASSIC_BLUE);
    public static final ThemeDef OBSIDIAN_BLACK = getTheme(THEME_OBSIDIAN_BLACK);
    public static final ThemeDef EMERALD_GREEN = getTheme(THEME_EMERALD_GREEN);
    public static final ThemeDef AMBER_GOLD = getTheme(THEME_AMBER_GOLD);
    public static final ThemeDef WINE_PURPLE = getTheme(THEME_WINE_PURPLE);
    public static final ThemeDef CYAN_SEA = getTheme(THEME_CYAN_SEA);

    private static ThemeProvider sThemeProvider;

    protected NokiaTheme() {
        // utility class
    }

    /**
     * 注入生态主题提供者实现。
     */
    public static synchronized void setThemeProvider(ThemeProvider provider) {
        sThemeProvider = provider;
    }

    /**
     * 获取当前生效的主题定义。
     */
    @NonNull
    public static ThemeDef getCurrentTheme(Context context) {
        if (sThemeProvider != null && context != null) {
            ThemeDef theme = sThemeProvider.getCurrentTheme(context);
            if (theme != null) return theme;
        }
        return getTheme(THEME_CLASSIC_BLUE);
    }

    /**
     * 主题颜色配置数据结构（生态统一 13 字段超集）。
     */
    public static class ThemeDef {
        public final String id;
        public final String name;

        // SDK 独立应用常用字段
        public final int primaryColor;
        public final int darkColor;
        public final int textColor;
        public final int subTextColor;
        public final int cardBgColor;

        // 桌面与通用控件常用字段
        public final int accentColor;
        public final int softKeyStartColor;
        public final int softKeyEndColor;
        public final int bgStartColor;
        public final int bgCenterColor;
        public final int bgEndColor;
        public final int focusColor;

        public Drawable createTitleDrawable() {
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{primaryColor, darkColor}
            );
        }

        public Drawable createSoftKeyDrawable() {
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{softKeyStartColor, softKeyEndColor}
            );
        }

        public Drawable createSelectedRowDrawable(float radiusPx) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(focusColor);
            gd.setCornerRadius(radiusPx);
            return gd;
        }

        public Drawable createDialogBodyDrawable() {
            // 与桌面一致：对话框内容区使用 bgCenter -> bgEnd 的渐变
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{bgCenterColor, bgEndColor}
            );
        }

        public Drawable createInputFieldDrawable(float strokePx, float radiusPx) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(bgStartColor);
            gd.setStroke((int) strokePx, accentColor);
            gd.setCornerRadius(radiusPx);
            return gd;
        }

        public ThemeDef(String name,
                        int primaryColor, int darkColor, int accentColor, int focusColor,
                        int textColor, int subTextColor, int cardBgColor) {
            this(name, name, primaryColor, darkColor, accentColor, focusColor,
                    textColor, subTextColor, cardBgColor,
                    primaryColor, darkColor, darkColor, primaryColor, darkColor);
        }

        public ThemeDef(String id, String name,
                        int primaryColor, int darkColor, int accentColor, int focusColor,
                        int textColor, int subTextColor, int cardBgColor,
                        int softKeyStartColor, int softKeyEndColor,
                        int bgStartColor, int bgCenterColor, int bgEndColor) {
            this.id = id;
            this.name = name;
            this.primaryColor = primaryColor;
            this.darkColor = darkColor;
            this.accentColor = accentColor;
            this.focusColor = focusColor;
            this.textColor = textColor;
            this.subTextColor = subTextColor;
            this.cardBgColor = cardBgColor;
            this.softKeyStartColor = softKeyStartColor;
            this.softKeyEndColor = softKeyEndColor;
            this.bgStartColor = bgStartColor;
            this.bgCenterColor = bgCenterColor;
            this.bgEndColor = bgEndColor;
        }
    }

    /**
     * 根据主题 ID 获取预设主题定义。
     */
    @NonNull
    public static ThemeDef getTheme(String themeId) {
        if (themeId == null) themeId = THEME_CLASSIC_BLUE;
        // D3：色调以桌面（keydroidx-launcher）调色板为准。
        // 7 个桌面字段（accent/softKey*/bg*/focus）完全复用桌面值；
        // primary/dark 取桌面软键渐变起止色；text/subText/cardBg 沿用生态通用浅色/半透明卡片值。
        switch (themeId) {
            case THEME_EMERALD_GREEN:
                return new ThemeDef(
                        THEME_EMERALD_GREEN, "翡翠幽绿",
                        0xFF144324, 0xFF0A1F11, 0xFF81C784, 0x664CAF50,
                        0xFFE8F5E9, 0xFFA5D6A7, 0xCC0A1F11,
                        0xFF144324, 0xFF0A1F11,
                        0xFF0A1F11, 0xFF144324, 0xFF0A1F11
                );
            case THEME_AMBER_GOLD:
                return new ThemeDef(
                        THEME_AMBER_GOLD, "琥珀暖金",
                        0xFF4A2D14, 0xFF241408, 0xFFFFB74D, 0x66FF9800,
                        0xFFFFF8E7, 0xFFFFE082, 0xCC241408,
                        0xFF4A2D14, 0xFF241408,
                        0xFF3E2704, 0xFF6D4708, 0xFF2A1A02
                );
            case THEME_OBSIDIAN_BLACK:
                return new ThemeDef(
                        THEME_OBSIDIAN_BLACK, "曜石纯黑",
                        0xFF212121, 0xFF000000, 0xFFB0BEC5, 0x6678909C,
                        0xFFECEFF1, 0xFF90A4AE, 0xCC000000,
                        0xFF212121, 0xFF000000,
                        0xFF0A0A0A, 0xFF1C1C1C, 0xFF050505
                );
            case THEME_CYAN_SEA:
                return new ThemeDef(
                        THEME_CYAN_SEA, "青海浩渺",
                        0xFF0B3D4F, 0xFF051C24, 0xFF4DD0E1, 0x6600BCD4,
                        0xFFE0F7FA, 0xFF80DEEA, 0xCC051C24,
                        0xFF0B3D4F, 0xFF051C24,
                        0xFF051C24, 0xFF0B3D4F, 0xFF051C24
                );
            case THEME_WINE_PURPLE:
                return new ThemeDef(
                        THEME_WINE_PURPLE, "典雅酒红",
                        0xFF4A153B, 0xFF21081A, 0xFFBA68C8, 0x669C27B0,
                        0xFFF3E5F5, 0xFFCE93D8, 0xCC21081A,
                        0xFF4A153B, 0xFF21081A,
                        0xFF21081A, 0xFF4A153B, 0xFF21081A
                );
            case THEME_CLASSIC_BLUE:
            default:
                return new ThemeDef(
                        THEME_CLASSIC_BLUE, "经典深蓝",
                        0xFF1A3A6B, 0xFF0D1B3E, 0xFF64B5F6, 0x662196F3,
                        0xFFE8EEF5, 0xFF90CAF9, 0xCC0D1B3E,
                        0xFF1A3A6B, 0xFF0D1B3E,
                        0xFF0D1B3E, 0xFF1A3A6B, 0xFF0D1B3E
                );
        }
    }

    // ==========================================
    // Drawable 构造工厂方法
    // ==========================================

    /**
     * 创建标题栏背景渐变 Drawable。
     */
    public static GradientDrawable createTitleDrawable(ThemeDef theme) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.primaryColor, theme.darkColor}
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        return gd;
    }

    public static GradientDrawable createTitleDrawable(Context context) {
        return createTitleDrawable(getCurrentTheme(context));
    }

    /**
     * 创建软键操作栏背景渐变 Drawable。
     */
    public static GradientDrawable createSoftKeyDrawable(ThemeDef theme) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.softKeyStartColor, theme.softKeyEndColor}
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        return gd;
    }

    public static GradientDrawable createSoftKeyDrawable(Context context) {
        return createSoftKeyDrawable(getCurrentTheme(context));
    }

    /**
     * 创建列表选中高亮条 Drawable。
     */
    public static GradientDrawable createSelectedRowDrawable(ThemeDef theme) {
        // 与桌面一致：高亮使用纯色半透明 focusColor
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(theme.focusColor);
        return gd;
    }

    public static GradientDrawable createSelectedRowDrawable(Context context) {
        return createSelectedRowDrawable(getCurrentTheme(context));
    }

    public static Drawable createSelectionDrawable(Context context, float radiusDp) {
        float density = context != null ? context.getResources().getDisplayMetrics().density : 1f;
        ThemeDef theme = getCurrentTheme(context);
        return theme.createSelectedRowDrawable(radiusDp * density);
    }

    /**
     * 创建桌面主壁纸三段式渐变 Drawable。
     */
    public static GradientDrawable createBackgroundDrawable(ThemeDef theme) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.bgStartColor, theme.bgCenterColor, theme.bgEndColor}
        );
    }

    /**
     * 创建复古对话框面板背景 Drawable。
     */
    public static GradientDrawable createDialogBodyDrawable(ThemeDef theme) {
        // 与桌面一致：对话框内容区使用 bgCenter -> bgEnd 渐变
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.bgCenterColor, theme.bgEndColor}
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        return gd;
    }

    /**
     * 创建聚焦框 StateListDrawable（用于按键选中焦点与普通状态切换）。
     */
    public static StateListDrawable createFocusDrawable(ThemeDef theme) {
        // 与桌面一致：焦点态使用纯色半透明 focusColor
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setColor(theme.focusColor);
        focused.setCornerRadius(0);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_focused}, focused);
        sld.addState(new int[]{android.R.attr.state_selected}, focused);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
