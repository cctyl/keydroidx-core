package io.github.cctyl.nokia.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * Material Icons 字体图标管理类。
 * 全生态共享统一的图标 Unicode 字典与渲染工具。
 */
public class NokiaIcons {

    protected NokiaIcons() {}

    private static Typeface sTypeface;

    // ==========================================
    // 基础系统与导航图标
    // ==========================================
    public static final String FOLDER = "\uE2C7";            // folder
    public static final String SETTINGS = "\uE8B8";          // settings
    public static final String HELP = "\uE8FD";              // help_outline
    public static final String INFO = "\uE88E";              // info_outline
    public static final String FEEDBACK = "\uE87F";          // feedback
    public static final String BUG_REPORT = "\uE868";        // bug_report
    public static final String ARROW_BACK = "\uE5C4";        // arrow_back
    public static final String ARROW_FORWARD = "\uE5C8";     // arrow_forward
    public static final String CHECK = "\uE5CA";             // check
    public static final String CLOSE = "\uE5CD";             // close
    public static final String EDIT = "\uE3C9";              // edit
    public static final String DELETE = "\uE872";            // delete
    public static final String REFRESH = "\uE5D5";           // refresh
    public static final String SEARCH = "\uE8B6";            // search
    public static final String WARNING = "\uE002";           // warning
    public static final String ERROR = "\uE000";             // error
    public static final String KEYBOARD = "\uE312";          // keyboard
    public static final String PALETTE = "\uE40A";           // palette
    public static final String TEXT_FORMAT = "\uE165";       // text_fields
    public static final String EXPAND_MORE = "\uE5CF";       // expand_more
    public static final String EXPAND_LESS = "\uE5CE";       // expand_less
    public static final String MORE_VERT = "\uE5D4";         // more_vert
    public static final String MORE_HORIZ = "\uE5D3";        // more_horiz
    public static final String STORAGE = "\uE1DB";           // storage
    public static final String PHONE_ANDROID = "\uE324";     // phone_android
    public static final String PERSON = "\uE7FD";            // person
    public static final String SHARE = "\uE80D";             // share
    public static final String STAR = "\uE838";              // star
    public static final String STAR_BORDER = "\uE83A";       // star_border
    public static final String FAVORITE = "\uE87D";          // favorite
    public static final String FAVORITE_BORDER = "\uE87E";   // favorite_border
    public static final String HOME = "\uE88A";              // home
    public static final String APPS = "\uE5C3";              // apps
    public static final String LIST = "\uE896";              // list
    public static final String VIEW_LIST = "\uE8EF";         // view_list
    public static final String SORT = "\uE164";              // sort
    public static final String FILTER_LIST = "\uE152";       // filter_list
    public static final String ADD = "\uE145";               // add
    public static final String REMOVE = "\uE15B";            // remove
    public static final String LOCK = "\uE897";              // lock
    public static final String LOCK_OPEN = "\uE898";         // lock_open
    public static final String POWER_SETTINGS = "\uE8AC";    // power_settings_new
    public static final String SYNC = "\uE627";              // sync
    public static final String CLOUD = "\uE2BD";             // cloud
    public static final String CLOUD_OFF = "\uE2C0";         // cloud_off
    public static final String CLOUD_UPLOAD = "\uE2C3";      // cloud_upload
    public static final String CLOUD_DOWNLOAD = "\uE2C0";    // cloud_download

    // ==========================================
    // 媒体与音乐播放图标
    // ==========================================
    public static final String MUSIC = "\uE405";             // music_note
    public static final String MUSIC_NOTE = "\uE405";
    public static final String PLAY = "\uE037";              // play_arrow
    public static final String PAUSE = "\uE034";             // pause
    public static final String SKIP_NEXT = "\uE044";         // skip_next
    public static final String SKIP_PREVIOUS = "\uE045";     // skip_previous
    public static final String FAST_FORWARD = "\uE01F";      // fast_forward
    public static final String FAST_REWIND = "\uE020";       // fast_rewind
    public static final String REPEAT = "\uE040";            // repeat
    public static final String REPEAT_ONE = "\uE041";        // repeat_one
    public static final String SHUFFLE = "\uE043";           // shuffle
    public static final String VOLUME_UP = "\uE050";         // volume_up
    public static final String VOLUME_DOWN = "\uE04D";       // volume_down
    public static final String VOLUME_MUTE = "\uE04E";       // volume_mute
    public static final String VOLUME_OFF = "\uE04F";        // volume_off
    public static final String ALBUM = "\uE019";             // album
    public static final String QUEUE_MUSIC = "\uE03D";       // queue_music
    public static final String PLAYLIST_PLAY = "\uE05F";     // playlist_play
    public static final String PLAYLIST_ADD = "\uE03B";      // playlist_add
    public static final String EQUALIZER = "\uE01D";         // equalizer
    public static final String HEADPHONES = "\uE310";        // headset
    public static final String RADIO = "\uE03E";             // radio
    public static final String MIC = "\uE029";               // mic

    // ==========================================
    // 桌面 Widget / 快捷磁贴 / 系统状态图标
    // ==========================================
    public static final String FLASHLIGHT_ON = "\uE3E4";     // highlight
    public static final String FLASHLIGHT_OFF = "\uE3E3";
    public static final String WIFI = "\uE63E";              // wifi
    public static final String WIFI_OFF = "\uE648";          // wifi_off
    public static final String BLUETOOTH = "\uE1A7";         // bluetooth
    public static final String BLUETOOTH_OFF = "\uE1AF";     // bluetooth_disabled
    public static final String DATA_USAGE = "\uE1AF";        // network_cell
    public static final String AIRPLANE_MODE = "\uE195";     // airplanemode_active
    public static final String BRIGHTNESS_HIGH = "\uE1AC";   // brightness_high
    public static final String BRIGHTNESS_MEDIUM = "\uE1AD"; // brightness_medium
    public static final String BRIGHTNESS_LOW = "\uE1AE";    // brightness_low
    public static final String BRIGHTNESS_AUTO = "\uE1AB";   // brightness_auto
    public static final String BATTERY_STD = "\uE1A3";       // battery_std
    public static final String BATTERY_CHARGING = "\uE1A4";  // battery_charging_full
    public static final String ACCESS_TIME = "\uE192";       // access_time
    public static final String CALENDAR_TODAY = "\uE935";    // calendar_today
    public static final String NOTIFICATIONS = "\uE7F4";     // notifications
    public static final String NOTIFICATIONS_OFF = "\uE7F6"; // notifications_off
    public static final String SIGNAL_CELLULAR = "\uE1F8";   // signal_cellular_4_bar

    // ==========================================
    // 兼容别名（ICON_ 前缀）
    // ==========================================
    public static final String ICON_EDIT = "\uE3C9";
    public static final String ICON_DONE = "\uE5CA";
    public static final String ICON_CHECK = "\uE5CA";
    public static final String ICON_CLOSE = "\uE5CD";
    public static final String ICON_DELETE = "\uE872";
    public static final String ICON_REFRESH = "\uE5D5";
    public static final String ICON_SEARCH = "\uE8B6";
    public static final String ICON_SETTINGS = "\uE8B8";
    public static final String ICON_HELP = "\uE8FD";
    public static final String ICON_INFO = "\uE88E";
    public static final String ICON_FEEDBACK = "\uE87F";
    public static final String ICON_BUG_REPORT = "\uE868";
    public static final String ICON_ARROW_BACK = "\uE5C4";
    public static final String ICON_ARROW_FORWARD = "\uE5C8";
    public static final String ICON_PLAY = "\uE037";
    public static final String ICON_PAUSE = "\uE034";
    public static final String ICON_SKIP_PREVIOUS = "\uE045";
    public static final String ICON_SKIP_NEXT = "\uE044";
    public static final String ICON_FAST_FORWARD = "\uE01F";
    public static final String ICON_FAST_REWIND = "\uE020";
    public static final String ICON_VOLUME_UP = "\uE050";
    public static final String ICON_VOLUME_DOWN = "\uE04D";
    public static final String ICON_VOLUME_MUTE = "\uE04E";
    public static final String ICON_MUSIC_NOTE = "\uE405";
    public static final String ICON_EQUALIZER = "\uE01D";
    public static final String ICON_QUEUE_MUSIC = "\uE03D";
    public static final String ICON_REPEAT = "\uE040";
    public static final String ICON_REPEAT_ONE = "\uE041";
    public static final String ICON_SHUFFLE = "\uE043";
    public static final String ICON_KEYBOARD = "\uE312";
    public static final String ICON_WARNING = "\uE002";
    public static final String ICON_ERROR = "\uE000";
    public static final String ICON_SIGNAL_CELLULAR_4_BAR = "\uF1C8";
    public static final String ICON_BATTERY_FULL = "\uE1A4";
    public static final String ICON_SD_CARD = "\uE623";
    public static final String ICON_FOLDER = "\uE2C7";
    public static final String ICON_PERSON = "\uE7FD";
    public static final String ICON_EXPLORE = "\uE87A";
    public static final String ICON_LEADERBOARD = "\uF20C";
    public static final String ICON_FAVORITE = "\uE87D";
    public static final String ICON_FAVORITE_BORDER = "\uE87E";
    public static final String ICON_HISTORY = "\uE889";
    public static final String ICON_DOWNLOAD = "\uE2C4";
    public static final String ICON_CHEVRON_RIGHT = "\uE5CC";

    // ==========================================
    // 原键桌面共享图标（自桌面 NokiaIcons 补齐，2026-08-28）
    // ==========================================
    public static final String ICON_ACTIVITY = "\uE879";     // extension (Activity快捷)
    public static final String ICON_ADD = "\uE145";          // add (加号)
    public static final String ICON_ADVANCED = "\uE869";     // build / tune (高级设置)
    public static final String ICON_APP = "\uE5C3";          // apps (应用网格)
    public static final String ICON_BG_MANAGER = "\uE53B";   // layers (后台管理/多任务)
    public static final String ICON_CALENDAR = "\uE935";     // calendar_today (日历)
    public static final String ICON_CHECK_BOX = "\uE834";    // check_box
    public static final String ICON_CHECK_BOX_OUTLINE_BLANK = "\uE835"; // check_box_outline_blank
    public static final String ICON_CLEAR_ALL = "\uE0B8";    // clear_all (清理后台)
    public static final String ICON_DESKTOP = "\uE871";      // desktop_windows (桌面)
    public static final String ICON_DISPLAY = "\uE3A5";      // display (显示设置)
    public static final String ICON_FONT = "\uE165";         // text_fields (字体设置)
    public static final String ICON_FREEZE = "\uEB3B";       // ac_unit (冻结)
    public static final String ICON_HOME = "\uE88A";         // home (桌面主屏)
    public static final String ICON_IP = "\uE894";           // language / public (IP地址)
    public static final String ICON_KEYPAD = "\uE312";       // dialpad (电话)
    public static final String ICON_LOCK = "\uE897";         // lock (锁屏)
    public static final String ICON_LOCK_OPEN = "\uE898";    // lock_open
    public static final String ICON_LOG = "\uE873";          // subject /日志
    public static final String ICON_MEMORY = "\uE30D";       // memory (内存)
    public static final String ICON_PALETTE = "\uE40A";      // palette (主题调色)
    public static final String ICON_POWER = "\uE8AC";        // power_settings_new (电源)
    public static final String ICON_POWER_OFF = "\uE8AC";    // power_settings_new
    public static final String ICON_POWER_ON = "\uE8AC";     // power_settings_new
    public static final String ICON_QS_TILE = "\uEA3B";      // tune (快捷开关磁贴)
    public static final String ICON_RESTORE = "\uE8B3";      // restore (恢复)
    public static final String ICON_SHIELD = "\uE8E8";       // security (保护/安全)
    public static final String ICON_SHIZUKU = "\uE869";      // build / tune (Shizuku开发)
    public static final String ICON_SHORTCUTS = "\uE8F9";    // shortcut (快捷栏)
    public static final String ICON_SORT = "\uE8D2";         // sort (排序)
    public static final String ICON_STORAGE = "\uE1DB";      // sd_storage (存储)
    public static final String ICON_SYSTEM = "\uE8B8";       // settings (系统)
    public static final String ICON_TERMINAL = "\uE869";     // build / tune (终端)
    public static final String ICON_TOGGLES = "\uEA3B";      // tune (快捷开关)
    public static final String ICON_URL = "\uE051";          // link (网址)
    public static final String ICON_USAGE = "\uE8B5";        // schedule (时钟/使用时长)
    public static final String ICON_WALLPAPER = "\uE3F4";    // image / wallpaper (壁纸设置)
    public static final String ICON_WIDGETS = "\uE871";      // widgets (桌面组件设置)
    public static final String TOGGLE_AIRPLANE = "\uE539";   // flight / airplanemode_active
    public static final String TOGGLE_BLUETOOTH = "\uE1A7";  // bluetooth
    // 亮度：按实际渲染效果映射，不按 Material 命名。
    // 在设备自带的 MaterialIcons-Regular.ttf 中，E1AD 视觉偏暗、E1AE 视觉偏亮，
    // 与官方命名正好相反；因此 LOW 用 E1AD，MEDIUM 用 E1AE。
    public static final String TOGGLE_BRIGHTNESS = "\uE1AC";        // brightness_high（兜底/默认态）
    public static final String TOGGLE_BRIGHTNESS_HIGH = "\uE1AC";   // brightness_high
    public static final String TOGGLE_BRIGHTNESS_MEDIUM = "\uE1AE"; // 官方名 brightness_low，实际视觉偏亮=中
    public static final String TOGGLE_BRIGHTNESS_LOW = "\uE1AD";      // 官方名 brightness_medium，实际视觉偏暗=低
    public static final String TOGGLE_BRIGHTNESS_AUTO = "\uE1AB";   // brightness_auto
    public static final String TOGGLE_CLEAN_BG = "\uE0B8";   // clear_all (清理后台)
    public static final String TOGGLE_DATA = "\uE1E2";       // swap_vert / data_usage
    public static final String TOGGLE_FREEZE = "\uEB3B";     // ac_unit (一键冻结)
    public static final String TOGGLE_HOTSPOT = "\uE1DA";    // wifi_tethering
    public static final String TOGGLE_LOCATION = "\uE0C8";   // location_on
    public static final String TOGGLE_LOCK = "\uE897";       // lock
    public static final String TOGGLE_ROTATE = "\uE84D";     // screen_rotation
    public static final String TOGGLE_SAVER = "\uE1A4";      // battery_saver
    public static final String TOGGLE_SOUND = "\uE050";      // volume_up
    public static final String TOGGLE_TORCH = "\uEF56";      // flashlight_on
    public static final String TOGGLE_UNFREEZE = "\uE430";   // wb_sunny (太阳/解冻/融化)
    public static final String TOGGLE_WIFI = "\uE63E";       // wifi
    // 电源类（需 mini_shizuku / shell 权限，瞬态动作无开关状态，触发前须二次确认）
    public static final String TOGGLE_SHUTDOWN = "\uE8AC";   // power_settings_new (关机)
    public static final String TOGGLE_REBOOT = "\uE042";     // replay (重启，单箭头绕圆)
    public static final String TOGGLE_RECOVERY = "\uE8C6";   // settings_power (重启到 Recovery)
    public static final String TOGGLE_FASTBOOT = "\uE869";   // build (重启到 Fastboot/Bootloader)
    public static final String ICON_ALBUM = "\uE019";
    public static final String ICON_LIBRARY_MUSIC = "\uE030";
    public static final String ICON_RADIO = "\uE03E";
    public static final String ICON_TODAY = "\uE8DF";
    public static final String ICON_STAR = "\uE838";
    public static final String ICON_SUBTITLES = "\uE048";
    public static final String ICON_PLAY_CIRCLE = "\uE01C";
    public static final String ICON_PLAY_CIRCLE_FILLED = "\uE038";
    public static final String ICON_LYRICS = "\uE26C";
    public static final String ICON_HOURGLASS = "\uE88B";

    /**
     * 获取 Material Icons Typeface（单例缓存）。
     */
    public static synchronized Typeface getTypeface(Context context) {
        if (sTypeface == null) {
            try {
                sTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/MaterialIcons-Regular.ttf");
            } catch (Exception e) {
                sTypeface = Typeface.DEFAULT;
            }
        }
        return sTypeface;
    }

    /**
     * 为指定的 TextView 应用 Material Icons 字体。
     */
    public static void applyTo(TextView textView) {
        if (textView == null) return;
        textView.setTypeface(getTypeface(textView.getContext()));
        textView.setIncludeFontPadding(false);
    }

    /**
     * 为 TextView 设置图标（同时应用字体与文本）。
     */
    public static void setIcon(TextView textView, String iconCode) {
        if (textView == null) return;
        applyTo(textView);
        textView.setText(iconCode);
    }

    /**
     * 获取一个固定尺寸和颜色的图标 Drawable。
     *
     * @param context   上下文
     * @param unicode   图标 Unicode
     * @param color     颜色
     * @param sizePx    像素大小
     * @return IconDrawable
     */
    public static Drawable createDrawable(Context context, String unicode, int sizePx, int color) {
        return new IconDrawable(getTypeface(context), unicode, color, sizePx);
    }

    /**
     * 兼容原键桌面的快捷获取 Drawable 方法（以 dp 指定尺寸）。
     */
    public static Drawable get(Context context, String unicode, int color, float sizeDp) {
        int sizePx = NokiaDimens.dp(context.getResources(), sizeDp);
        return new IconDrawable(getTypeface(context), unicode, color, sizePx);
    }

    /**
     * 创建默认 24dp 尺寸的矢量字体 Drawable（原键桌面 API）。
     */
    public static Drawable get(Context context, String unicode, int color) {
        return get(context, unicode, color, 24f);
    }

    /**
     * 纯矢量字体图标 Drawable 实现，支持 1:1 像素光栅化、任意染色与 Bounds 计算。
     * （实现来自原键桌面实战版本：绘制时按 bounds 自适应字号，基线垂直居中。）
     */
    public static class IconDrawable extends Drawable {
        private final Typeface typeface;
        private final String text;
        private final TextPaint paint;
        private final int sizePx;
        private final Rect textBounds = new Rect();

        public IconDrawable(Typeface typeface, String text, int color, int sizePx) {
            this.typeface = typeface;
            this.text = text;
            this.sizePx = sizePx;

            this.paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            this.paint.setTypeface(typeface);
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setColor(color);
            this.paint.setTextSize(sizePx);
            setBounds(0, 0, sizePx, sizePx);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            paint.setTextSize(Math.min(bounds.width(), bounds.height()));
            paint.getTextBounds(text, 0, text.length(), textBounds);

            float x = bounds.exactCenterX();
            // 垂直居中基线计算
            float y = bounds.exactCenterY() - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(text, x, y, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return sizePx;
        }

        @Override
        public int getIntrinsicHeight() {
            return sizePx;
        }

        public void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }
    }
}
