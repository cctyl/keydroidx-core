package io.github.cctyl.nokia.common.ui.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * 诺基亚风格像素虚线 Drawable。
 * <p>
 * 采用 {@code drawRect} 循环绘制方块点阵，避免硬件加速或低 DPI 屏幕下
 * {@link android.graphics.DashPathEffect} 被羽化为实线的问题。
 */
public class NokiaDashedLineDrawable extends Drawable {

    private final Paint mPaint = new Paint();
    private final int mDotSize;
    private final int mGapSize;

    public NokiaDashedLineDrawable(Resources res, int color) {
        this(res, color, 1f, 2f);
    }

    public NokiaDashedLineDrawable(Resources res, int color, float dotSizeDp, float gapSizeDp) {
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(false);

        mDotSize = Math.max(1, NokiaDimens.dp(res, dotSizeDp));
        mGapSize = Math.max(1, NokiaDimens.dp(res, gapSizeDp));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int step = mDotSize + mGapSize;
        int top = bounds.top;
        int bottom = top + mDotSize;

        for (int x = bounds.left; x < bounds.right; x += step) {
            int right = Math.min(x + mDotSize, bounds.right);
            canvas.drawRect(x, top, right, bottom, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return mDotSize;
    }
}
