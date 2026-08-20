package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 诺基亚按键机应用 BaseActivity
 * 严格遵循 NOKIA_DEVELOPMENT_RULES.md 规范：
 * 1. DPI 吸附对齐（消除低分屏 136DPI/ldpi 亚像素模糊）
 * 2. 剥离系统字体缩放（强制 fontScale=1.0f 消除系统大字体造成的排版溢出）
 * 3. 顶栏 22dp (#303030 + 12dp 白字 + 自动走时时钟)
 * 4. 底栏 22dp (#001166 + 12dp 白字 + 三栏布局 + 中间字号自适应压缩)
 * 5. 全屏沉浸与物理按键事件分发安全对齐
 */
public abstract class NokiaBaseActivity extends AppCompatActivity {

    private NokiaKeyClient keyClient;
    private int lastHandledDownKeyCode = -1;

    protected FrameLayout midPanel;
    protected View topPanel;
    protected View bottomPanel;
    protected TextView tvSoftLeft;
    protected TextView tvSoftCenter;
    protected TextView tvSoftRight;
    protected TextView tvTitle;
    protected TextView tvTime;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null && tvTime.getVisibility() == View.VISIBLE) {
                tvTime.setText(timeFormat.format(new Date()));
            }
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = newBase.getResources().getConfiguration();
        int dpi = config.densityDpi;
        int fixed = dpi;
        int[] standards = {120, 160, 213, 240, 320, 480, 640};
        boolean standard = false;
        for (int s : standards) {
            if (s == dpi) {
                standard = true;
                break;
            }
        }
        if (dpi < 160) {
            // ldpi/136dpi 等向上吸附到 mdpi(160)，彻底消除亚像素插值模糊
            fixed = 160;
        } else if (!standard) {
            int nearest = standards[0];
            int minDiff = Math.abs(dpi - nearest);
            for (int s : standards) {
                int diff = Math.abs(dpi - s);
                if (diff < minDiff) {
                    minDiff = diff;
                    nearest = s;
                }
            }
            fixed = nearest;
        }

        Configuration newConfig = new Configuration(config);
        newConfig.densityDpi = fixed;
        // 关键：强制设置 fontScale = 1.0f，防止系统「大字体/特大字体」导致界面溢出截断
        newConfig.fontScale = 1.0f;
        super.attachBaseContext(newBase.createConfigurationContext(newConfig));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        keyClient = NokiaKeyClient.get(this);
        setContentView(R.layout.activity_nokia_base);

        topPanel = findViewById(R.id.topPanel);
        midPanel = findViewById(R.id.midPanel);
        bottomPanel = findViewById(R.id.bottomPanel);
        tvSoftLeft = findViewById(R.id.tvSoftLeft);
        tvSoftCenter = findViewById(R.id.tvSoftCenter);
        tvSoftRight = findViewById(R.id.tvSoftRight);
        tvTitle = findViewById(R.id.tvPageTitle);
        tvTime = findViewById(R.id.tvTime);

        int contentRes = getContentLayoutRes();
        if (contentRes != 0 && midPanel != null) {
            midPanel.removeAllViews();
            getLayoutInflater().inflate(contentRes, midPanel, true);
        }

        setupSoftKeys();
        onInitViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    @LayoutRes
    protected abstract int getContentLayoutRes();

    protected abstract void onInitViews();

    protected void setupSoftKeys() {
        if (tvSoftLeft != null) {
            tvSoftLeft.setOnClickListener(v -> onAction(NokiaKeyAction.ACTION_SOFT_LEFT));
        }
        if (tvSoftRight != null) {
            tvSoftRight.setOnClickListener(v -> onAction(NokiaKeyAction.ACTION_SOFT_RIGHT));
        }
        if (tvSoftCenter != null) {
            tvSoftCenter.setOnClickListener(v -> onAction(NokiaKeyAction.ACTION_SELECT));
        }
    }

    /**
     * 设置底部栏三按钮文字。
     * 空文本使用 INVISIBLE 占位，保证三栏 weight 比例不崩塌且不触发误触。
     */
    public void setSoftKeys(@Nullable String left, @Nullable String center, @Nullable String right) {
        applySoftKeyText(tvSoftLeft, left, false);
        applySoftKeyText(tvSoftCenter, center, true);
        applySoftKeyText(tvSoftRight, right, false);
    }

    private void applySoftKeyText(@Nullable TextView tv, @Nullable String text, boolean isCenter) {
        if (tv == null) return;
        if (text == null || text.trim().isEmpty()) {
            tv.setVisibility(View.INVISIBLE);
        } else {
            tv.setText(text);
            tv.setVisibility(View.VISIBLE);
            if (isCenter) {
                fitCenterTextToWidth(tv);
            }
        }
    }

    /**
     * 动态测量并压缩底部中键文字大小，防止长标题（如"桌面组件设置"）被截断
     */
    private void fitCenterTextToWidth(final TextView tv) {
        tv.post(() -> {
            if (tv.getVisibility() != View.VISIBLE || tv.getWidth() <= 0) {
                return;
            }
            final float density = getResources().getDisplayMetrics().density;
            final Paint paint = tv.getPaint();
            while (tv.getTextSize() > 6f * density) {
                if (paint.measureText(tv.getText().toString()) <= tv.getWidth()) {
                    break;
                }
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                        Math.max(6f, tv.getTextSize() / density - 0.5f));
            }
        });
    }

    public void setPageTitle(@Nullable String title) {
        if (tvTitle != null) {
            tvTitle.setText(title != null ? title : "");
        }
    }

    public void setTopTimeVisible(boolean visible) {
        if (tvTime != null) {
            tvTime.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            NokiaKeyBinding binding = keyClient.getBinding();
            int action = binding.resolveAction(event);
            if (action >= 0) {
                boolean handled = onAction(action);
                if (handled) {
                    lastHandledDownKeyCode = keyCode;
                    return true;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == lastHandledDownKeyCode) {
                lastHandledDownKeyCode = -1;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    protected boolean onAction(int action) {
        if (action == NokiaKeyAction.ACTION_SOFT_RIGHT) {
            finish();
            return true;
        }
        return false;
    }
}
