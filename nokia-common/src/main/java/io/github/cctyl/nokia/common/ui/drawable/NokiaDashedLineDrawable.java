package io.github.cctyl.nokia.common.ui.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * 诺基亚风格像素虚线 Drawable。
 *
 * 使用 {@code drawRect} 循环绘制实心方块点阵，替代 DashPathEffect，彻底规避以下已知问题：
 * 1. DashPathEffect + ANTI_ALIAS 在 1px 线宽下 dash 边缘被羽化糊成实线（240×320 mdpi）；
 * 2. 硬件加速 Canvas 下 DashPathEffect 在 Android 4.4 等旧设备上可能画成实线或不渲染；
 * 3. Resources.getSystem() 绕过了 attachBaseContext 的 density 修正。
 *
 * 点宽和间隔在 dp 换算后保证 ≥1px，确保低密度屏幕下点阵始终可见。
 * 点阵在 bounds 内纵向居中绘制（来自原键桌面的实战版本）。
 */
public class NokiaDashedLineDrawable extends Drawable {

    private final Paint mPaint = new Paint();
    private final float mDotPx;   // 单个点宽度（px）
    private final float mGapPx;   // 间隔宽度（px）

    public NokiaDashedLineDrawable(Resources res, int color) {
        this(res, color, 1f, 2f);
    }

    /**
     * @param res       传入调用方的 Resources（接受 attachBaseContext 的 density 修正），禁止用 Resources.getSystem()
     * @param color     点阵颜色（含 alpha）
     * @param dotSizeDp 点宽（dp）
     * @param gapSizeDp 间隔（dp）
     */
    public NokiaDashedLineDrawable(Resources res, int color, float dotSizeDp, float gapSizeDp) {
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.FILL);  // FILL 无抗锯齿羽化，drawRect 全版本硬件加速正常
        mPaint.setAntiAlias(false);         // 点阵无需抗锯齿
        mDotPx = Math.max(1f, NokiaDimens.dpF(res, dotSizeDp));
        mGapPx = Math.max(1f, NokiaDimens.dpF(res, gapSizeDp));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int left = getBounds().left;
        int right = getBounds().right;
        int h = getBounds().height();
        float y = h / 2f - 0.5f;            // 纵向居中（dotPx 通常 1~3px，对齐半像素可更清晰）
        float step = mDotPx + mGapPx;
        float x = left;

        while (x < right) {
            float drawEnd = Math.min(x + mDotPx, right);
            canvas.drawRect(x, y, drawEnd, y + mDotPx, mPaint);
            x += step;
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
        return (int) mDotPx;
    }
}
