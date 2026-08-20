package io.github.cctyl.nokia.keycore.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 诺基亚经典风格 1px 虚线分割线 Drawable（规避 Android 硬件加速下 Shape 虚线失效变实线的问题）
 */
public class NokiaDashedLineDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NokiaDashedLineDrawable(int color, float strokeWidthPx, float dashWidthPx, float dashGapPx) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidthPx > 0 ? strokeWidthPx : 1f);
        paint.setPathEffect(new DashPathEffect(new float[]{dashWidthPx, dashGapPx}, 0));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float y = getBounds().exactCenterY();
        canvas.drawLine(getBounds().left, y, getBounds().right, y, paint);
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
}
