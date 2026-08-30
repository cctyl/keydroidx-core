package io.github.cctyl.nokia.common.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * 诺基亚经典复古电池 Drawable。
 * <p>
 * 支持：
 * <ul>
 *   <li>4 格电量状态显示与动态变色（低电量红色）；</li>
 *   <li>充电闪电图标覆盖指示；</li>
 *   <li>纯矢量绘制，零图片资源依赖。</li>
 * </ul>
 */
public class NokiaBatteryDrawable extends Drawable {

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path boltPath = new Path();

    private final RectF bodyRect = new RectF();
    private final RectF capRect = new RectF();
    private final RectF barRect = new RectF();

    private int levelPct = 100;
    private boolean isCharging = false;
    private boolean isPowerSaveMode = false;
    private int customWidth = -1;
    private int customHeight = -1;

    /** 省电模式电量格颜色（醒目黄）。 */
    private static final int COLOR_POWER_SAVE = 0xFFFFC107;
    /** 低电量告警红。 */
    private static final int COLOR_LOW = 0xFFF44336;
    /** 正常电量绿。 */
    private static final int COLOR_NORMAL = 0xFF4CAF50;
    /** 充电中亮绿。 */
    private static final int COLOR_CHARGING = 0xFF00E676;

    public NokiaBatteryDrawable(Context context) {
        float density = context != null ? context.getResources().getDisplayMetrics().density : 1f;
        initPaints(density, 0xFFFFFFFF);
    }

    public NokiaBatteryDrawable(Resources res, int widthPx, int heightPx, int strokeColor) {
        float density = res != null ? res.getDisplayMetrics().density : 1f;
        this.customWidth = widthPx;
        this.customHeight = heightPx;
        initPaints(density, strokeColor);
    }

    private void initPaints(float density, int strokeColor) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(strokeColor);
        strokePaint.setStrokeWidth(Math.max(1f, density));

        fillPaint.setStyle(Paint.Style.FILL);

        boltPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        boltPaint.setColor(0xFFFFEB3B);
        boltPaint.setStrokeWidth(Math.max(0.5f, density * 0.5f));
        boltPaint.setStrokeJoin(Paint.Join.ROUND);
        boltPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBatteryState(int pct, boolean charging) {
        if (this.levelPct != pct || this.isCharging != charging) {
            this.levelPct = pct;
            this.isCharging = charging;
            invalidateSelf();
        }
    }

    /**
     * 设置省电模式开关。开启后电量格变为黄色（而非绿色），与系统省电状态保持一致。
     * 优先级高于充电态的亮绿，但充电闪电仍照常绘制。
     */
    public void setPowerSaveMode(boolean powerSave) {
        if (this.isPowerSaveMode != powerSave) {
            this.isPowerSaveMode = powerSave;
            invalidateSelf();
        }
    }

    public void updateLevel(int pct, boolean charging) {
        setBatteryState(pct, charging);
    }

    @Override
    public int getIntrinsicWidth() {
        return customWidth > 0 ? customWidth : 36;
    }

    @Override
    public int getIntrinsicHeight() {
        return customHeight > 0 ? customHeight : 20;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) return;

        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        float strokeW = strokePaint.getStrokeWidth();
        float padY = h * 0.15f;
        float bodyW = w * 0.80f;
        float bodyH = h - padY * 2;
        float capW = w * 0.10f;
        float capH = bodyH * 0.48f;

        // 1. 电池外壳
        bodyRect.set(strokeW / 2, padY + strokeW / 2, bodyW - strokeW / 2, padY + bodyH - strokeW / 2);
        float cornerRadius = bodyH * 0.12f;
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, strokePaint);

        // 2. 电池正极帽
        float capX = bodyW + 1;
        float capY = padY + (bodyH - capH) / 2;
        capRect.set(capX, capY, Math.min(capX + capW, w - 1), capY + capH);
        strokePaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(capRect, cornerRadius * 0.8f, cornerRadius * 0.8f, strokePaint);
        strokePaint.setStyle(Paint.Style.STROKE);

        // 3. 内部电量格 (4 格电池)
        int totalBars = 4;
        int activeBars;
        int barColor;

        if (levelPct <= 10) {
            activeBars = 1;
            barColor = COLOR_LOW;
        } else if (levelPct <= 25) {
            activeBars = 1;
            barColor = COLOR_NORMAL;
        } else if (levelPct <= 50) {
            activeBars = 2;
            barColor = COLOR_NORMAL;
        } else if (levelPct <= 75) {
            activeBars = 3;
            barColor = COLOR_NORMAL;
        } else {
            activeBars = 4;
            barColor = COLOR_NORMAL;
        }

        // 充电中显示亮绿。
        if (isCharging) {
            barColor = COLOR_CHARGING;
        }
        // 省电模式优先：电量格统一变黄（低电量红告警仍保留，因其语义为危险）。
        if (isPowerSaveMode && levelPct > 10) {
            barColor = COLOR_POWER_SAVE;
        }

        fillPaint.setColor(barColor);

        float innerMargin = strokeW + 1.5f;
        float innerX = bodyRect.left + innerMargin;
        float innerY = bodyRect.top + innerMargin;
        float innerW = bodyRect.width() - innerMargin * 2;
        float innerH = bodyRect.height() - innerMargin * 2;

        float barGap = innerW * 0.08f;
        float singleBarW = (innerW - barGap * (totalBars - 1)) / totalBars;

        for (int i = 0; i < activeBars; i++) {
            float bx = innerX + i * (singleBarW + barGap);
            barRect.set(bx, innerY, bx + singleBarW, innerY + innerH);
            canvas.drawRect(barRect, fillPaint);
        }

        // 4. 充电中闪电图标
        if (isCharging) {
            boltPath.reset();
            float cx = innerX + innerW * 0.5f;
            float cy = innerY + innerH * 0.5f;
            float bw = innerW * 0.55f;
            float bh = innerH * 1.15f;

            boltPath.moveTo(cx + bw * 0.15f, cy - bh * 0.50f);
            boltPath.lineTo(cx - bw * 0.40f, cy + bh * 0.05f);
            boltPath.lineTo(cx - bw * 0.05f, cy + bh * 0.05f);
            boltPath.lineTo(cx - bw * 0.20f, cy + bh * 0.50f);
            boltPath.lineTo(cx + bw * 0.40f, cy - bh * 0.05f);
            boltPath.lineTo(cx + bw * 0.05f, cy - bh * 0.05f);
            boltPath.close();

            boltPaint.setColor(0xCC000000);
            boltPaint.setStyle(Paint.Style.STROKE);
            boltPaint.setStrokeWidth(strokeW * 1.5f);
            canvas.drawPath(boltPath, boltPaint);

            boltPaint.setColor(0xFFFFEB3B);
            boltPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(boltPath, boltPaint);
        }

        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
        boltPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        strokePaint.setColorFilter(colorFilter);
        fillPaint.setColorFilter(colorFilter);
        boltPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
