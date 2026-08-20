package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诺基亚复古主题系统。
 * 定义 6 套经典机身/壁纸主题色彩及渐变、卡片背景、高亮色，与桌面端完全一致。
 */
public class NokiaTheme {

    public static final String THEME_CLASSIC_BLUE = "classic_blue";
    public static final String THEME_OBSIDIAN_BLACK = "obsidian_black";
    public static final String THEME_CYAN_SEA = "cyan_sea";
    public static final String THEME_EMERALD_GREEN = "emerald_green";
    public static final String THEME_WINE_PURPLE = "wine_purple";
    public static final String THEME_AMBER_GOLD = "amber_gold";

    public static class ThemeDef {
        public final String id;
        public final String name;
        @ColorInt public final int primaryColor;
        @ColorInt public final int darkColor;
        @ColorInt public final int accentColor;
        @ColorInt public final int focusColor;
        @ColorInt public final int textColor;
        @ColorInt public final int subTextColor;
        @ColorInt public final int cardBgColor;

        public ThemeDef(String id, String name, int primaryColor, int darkColor,
                        int accentColor, int focusColor, int textColor, int subTextColor, int cardBgColor) {
            this.id = id;
            this.name = name;
            this.primaryColor = primaryColor;
            this.darkColor = darkColor;
            this.accentColor = accentColor;
            this.focusColor = focusColor;
            this.textColor = textColor;
            this.subTextColor = subTextColor;
            this.cardBgColor = cardBgColor;
        }

        public Drawable createTitleDrawable() {
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{primaryColor, darkColor}
            );
        }

        public Drawable createSoftKeyDrawable() {
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{primaryColor, darkColor}
            );
        }

        public Drawable createSelectedRowDrawable(float radiusPx) {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(focusColor);
            gd.setCornerRadius(radiusPx);
            return gd;
        }
    }

    private static final Map<String, ThemeDef> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put(THEME_CLASSIC_BLUE, new ThemeDef(
                THEME_CLASSIC_BLUE, "经典深蓝",
                Color.parseColor("#1a3a6b"), Color.parseColor("#0d1b3e"),
                Color.parseColor("#0055AA"), Color.parseColor("#0055AA"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#B0B0B0"),
                Color.parseColor("#F01E1E1E")
        ));

        THEMES.put(THEME_OBSIDIAN_BLACK, new ThemeDef(
                THEME_OBSIDIAN_BLACK, "曜石纯黑",
                Color.parseColor("#2D2D2D"), Color.parseColor("#141414"),
                Color.parseColor("#4A4A4A"), Color.parseColor("#333333"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#A0A0A0"),
                Color.parseColor("#F0141414")
        ));

        THEMES.put(THEME_CYAN_SEA, new ThemeDef(
                THEME_CYAN_SEA, "青海浩渺",
                Color.parseColor("#005A70"), Color.parseColor("#002A35"),
                Color.parseColor("#00838F"), Color.parseColor("#00838F"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#80DEEA"),
                Color.parseColor("#F0002A35")
        ));

        THEMES.put(THEME_EMERALD_GREEN, new ThemeDef(
                THEME_EMERALD_GREEN, "翡翠幽绿",
                Color.parseColor("#1B4D2E"), Color.parseColor("#0C2616"),
                Color.parseColor("#2E7D32"), Color.parseColor("#2E7D32"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#A5D6A7"),
                Color.parseColor("#F00C2616")
        ));

        THEMES.put(THEME_WINE_PURPLE, new ThemeDef(
                THEME_WINE_PURPLE, "典雅酒红",
                Color.parseColor("#4A154B"), Color.parseColor("#250826"),
                Color.parseColor("#6A1B9A"), Color.parseColor("#6A1B9A"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#CE93D8"),
                Color.parseColor("#F0250826")
        ));

        THEMES.put(THEME_AMBER_GOLD, new ThemeDef(
                THEME_AMBER_GOLD, "琥珀暖金",
                Color.parseColor("#5C4300"), Color.parseColor("#2E2000"),
                Color.parseColor("#F57F17"), Color.parseColor("#E65100"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#FFE082"),
                Color.parseColor("#F02E2000")
        ));
    }

    public static ThemeDef getTheme(String themeId) {
        ThemeDef theme = THEMES.get(themeId);
        if (theme == null) {
            theme = THEMES.get(THEME_CLASSIC_BLUE);
        }
        return theme;
    }

    public static Map<String, ThemeDef> getAllThemes() {
        return THEMES;
    }
}
