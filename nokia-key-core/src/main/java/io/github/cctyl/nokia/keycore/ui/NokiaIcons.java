package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 诺基亚复古风格 Material Icons 矢量图标辅助类。
 * 基于 Google Material Icons 字体（MaterialIcons-Regular.ttf），支持 2500+ 矢量图标。
 */
public class NokiaIcons {

    private static final String FONT_PATH = "fonts/MaterialIcons-Regular.ttf";
    private static Typeface sTypeface;

    public static final String ICON_SEARCH = "";
    public static final String ICON_SETTINGS = "";
    public static final String ICON_HOME = "";
    public static final String ICON_MENU = "";
    public static final String ICON_MORE_VERT = "";
    public static final String ICON_MORE_HORIZ = "";
    public static final String ICON_REFRESH = "";
    public static final String ICON_CLOSE = "";
    public static final String ICON_CHECK = "";
    public static final String ICON_ARROW_BACK = "";
    public static final String ICON_ARROW_FORWARD = "";
    public static final String ICON_KEYBOARD_ARROW_UP = "";
    public static final String ICON_KEYBOARD_ARROW_DOWN = "";
    public static final String ICON_KEYBOARD_ARROW_LEFT = "";
    public static final String ICON_KEYBOARD_ARROW_RIGHT = "";
    public static final String ICON_FOLDER = "";
    public static final String ICON_FOLDER_OPEN = "";
    public static final String ICON_DELETE = "";
    public static final String ICON_EDIT = "";
    public static final String ICON_ADD = "";
    public static final String ICON_INFO = "";
    public static final String ICON_HELP = "";
    public static final String ICON_WARNING = "";
    public static final String ICON_ERROR = "";
    public static final String ICON_LOCK = "";
    public static final String ICON_PHONE = "";
    public static final String ICON_MUSIC_NOTE = "";
    public static final String ICON_PLAY = "";
    public static final String ICON_PAUSE = "";
    public static final String ICON_STOP = "";
    public static final String ICON_SKIP_NEXT = "";
    public static final String ICON_SKIP_PREVIOUS = "";
    public static final String ICON_VOLUME_UP = "";
    public static final String ICON_VOLUME_OFF = "";
    public static final String ICON_REPEAT = "";
    public static final String ICON_REPEAT_ONE = "";
    public static final String ICON_SHUFFLE = "";
    public static final String ICON_PALETTE = "";
    public static final String ICON_TEXT_FIELDS = "";
    public static final String ICON_KEYBOARD = "";
    public static final String ICON_CHECK_CIRCLE = "";
    public static final String ICON_RADIO_BUTTON_UNCHECKED = "";
    public static final String ICON_RADIO_BUTTON_CHECKED = "";
    public static final String ICON_STAR = "";
    public static final String ICON_FAVORITE = "";
    public static final String ICON_FAVORITE_BORDER = "\uE87E";
    public static final String ICON_PLAY_CIRCLE = "\uE01C";
    public static final String ICON_PLAY_CIRCLE_FILLED = "\uE038";
    public static final String ICON_SIGNAL_CELLULAR_4_BAR = "\uF1C8";

    // 音乐/页面常用图标
    public static final String ICON_PERSON = "\uE7FD";
    public static final String ICON_EXPLORE = "\uE87A";
    public static final String ICON_LEADERBOARD = "\uF20C";
    public static final String ICON_HISTORY = "\uE889";
    public static final String ICON_SD_CARD = "\uE623";
    public static final String ICON_ALBUM = "\uE019";
    public static final String ICON_QUEUE_MUSIC = "\uE03D";
    public static final String ICON_LIBRARY_MUSIC = "\uE030";
    public static final String ICON_RADIO = "\uE03E";
    public static final String ICON_TODAY = "\uE8DF";
    public static final String ICON_CHEVRON_RIGHT = "\uE5CC";
    public static final String ICON_SUBTITLES = "\uE048";
    public static final String ICON_LYRICS = "\uE26C";

    public static synchronized Typeface getTypeface(Context context) {
        if (sTypeface == null && context != null) {
            try {
                sTypeface = Typeface.createFromAsset(context.getAssets(), FONT_PATH);
            } catch (Exception e) {
                sTypeface = Typeface.DEFAULT;
            }
        }
        return sTypeface;
    }

    public static void applyTo(TextView textView) {
        if (textView == null) return;
        Typeface tf = getTypeface(textView.getContext());
        if (tf != null) {
            textView.setTypeface(tf);
        }
    }

    public static void setIcon(TextView textView, String iconCode) {
        if (textView == null) return;
        applyTo(textView);
        textView.setText(iconCode);
    }

    public static IconDrawable createDrawable(Context context, String iconCode, int sizePx, @ColorInt int color) {
        return new IconDrawable(context, iconCode, sizePx, color);
    }

    public static class IconDrawable extends Drawable {
        private final Paint paint;
        private final String iconCode;
        private final int size;

        public IconDrawable(Context context, String iconCode, int sizePx, @ColorInt int color) {
            this.iconCode = iconCode;
            this.size = sizePx;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setTypeface(NokiaIcons.getTypeface(context));
            this.paint.setColor(color);
            this.paint.setTextAlign(Paint.Align.CENTER);
            this.paint.setTextSize(sizePx);
            setBounds(0, 0, sizePx, sizePx);
        }

        public void setColor(@ColorInt int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float x = bounds.exactCenterX();
            Paint.FontMetrics fm = paint.getFontMetrics();
            float y = bounds.exactCenterY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(iconCode, x, y, paint);
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
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }
}
