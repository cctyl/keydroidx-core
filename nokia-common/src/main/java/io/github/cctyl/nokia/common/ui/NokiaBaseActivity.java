package io.github.cctyl.nokia.common.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import io.github.cctyl.nokia.common.log.NokiaLog;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.model.DefaultKeyResolver;
import io.github.cctyl.nokia.common.model.KeyResolver;
import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.page.NokiaPage;
import io.github.cctyl.nokia.common.ui.page.NokiaPageHost;
import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * 诺基亚复古风格基类 Activity（通用纯骨架）。
 * <p>
 * 封装 240dp 基准复古骨架、DPI 亚像素对齐吸附、全屏沉浸、时钟打点、电量广播、
 * 标题栏、三段式软键条、Touch Mode 首键防吞噬、页面契约（{@link NokiaPage}）与防抖物理按键分发。
 * <p>
 * 零宿主业务依赖，桌面 Launcher 与独立 App（key-core）均继承此基类。
 */
public abstract class NokiaBaseActivity extends AppCompatActivity implements NokiaPageHost, KeyResolver {

    private static final String TAG = "NokiaBaseActivity";
    private static final long DEBOUNCE_MS = 60;

    protected View rootContainer;
    protected View titleBar;
    protected TextView tvTitle;
    protected TextView tvTitleIcon;
    protected View statusBar;
    protected TextView tvSignalIcon;
    protected ImageView tvBatteryIcon;
    protected TextView tvBatteryPercent;
    protected FrameLayout contentContainer;
    protected View bottomBar;
    protected TextView tvSoftLeft;
    protected TextView tvSoftCenter;
    protected TextView tvSoftRight;

    // 时钟打点支持（桌面/包含 tvTime 的布局自动启用）
    private TextView tvTime;
    private final Handler clockHandler = new Handler();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                tvTime.setText(fmt.format(new Date()));
            }
            clockHandler.postDelayed(this, 1000);
        }
    };

    private int lastDownKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long lastDownTime = 0;
    private boolean lastDownHandled = false;

    private BroadcastReceiver batteryReceiver;

    /**
     * 部分低分辨率设备（如 320x480 且系统 density 非标准，例如 136 DPI → density=0.85）
     * 会让所有 dp 尺寸落在亚像素位置，被抗锯齿虚化成灰边，导致图标发虚。
     * 这里把 density 吸附到标准的 1.0（mdpi），物理布局完全不变（240dp 设计仍铺满屏幕），
     * 但所有尺寸对齐到整数像素，彻底消除亚像素模糊。高 DPI 设备（density 已是整数倍）不受影响。
     */
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
        if (fixed != dpi) {
            newConfig.densityDpi = fixed;
        }
        newConfig.fontScale = 1.0f;
        super.attachBaseContext(newBase.createConfigurationContext(newConfig));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int baseLayout = getBaseLayoutRes();
        if (baseLayout != 0) {
            setContentView(baseLayout);
        }

        // 真机全屏：必须在 setContentView 之后调用（getInsetsController 依赖 DecorView）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
                insetsController.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // 兜底全屏：legacy systemUiVisibility（在部分设备/ROM 上比 InsetsController 更稳）
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        bindViews();

        int contentRes = getContentLayoutRes();
        if (contentRes != 0 && contentContainer != null) {
            LayoutInflater.from(this).inflate(contentRes, contentContainer, true);
        }

        onInitViews();

        // 软键默认点击事件 (触屏回退)
        if (tvSoftLeft != null) {
            tvSoftLeft.setOnClickListener(v -> onAction(NokiaKeyAction.SOFT_LEFT));
        }
        if (tvSoftCenter != null) {
            tvSoftCenter.setOnClickListener(v -> onAction(NokiaKeyAction.SELECT));
        }
        if (tvSoftRight != null) {
            tvSoftRight.setOnClickListener(v -> onAction(NokiaKeyAction.SOFT_RIGHT));
        }

        // 主动触发一次主题与字体应用
        NokiaTheme.ThemeDef currentTheme = NokiaUi.getTheme(this);
        if (currentTheme != null) {
            applyTheme(currentTheme);
        }
        NokiaFontManager.applyToViewTree(getWindow().getDecorView());

        // 确保内容容器具备在 touch mode 下持焦能力
        if (contentContainer != null) {
            contentContainer.setFocusable(true);
            contentContainer.setFocusableInTouchMode(true);
        }
        ensureActiveFocus();
    }

    /**
     * 骨架布局 ID。默认使用 SDK 内置三段式骨架布局 {@code R.layout.activity_nokia_base}。
     * 子类（如桌面 Launcher 自己的完整窗口）可重写返回 0 或自定义布局。
     */
    @LayoutRes
    protected int getBaseLayoutRes() {
        return R.layout.activity_nokia_base;
    }

    /**
     * 绑定三段式面板常用 View（兼容 key-core 与 launcher 两套命名）。
     */
    protected void bindViews() {
        rootContainer = findViewById(R.id.midPanel);
        titleBar = findViewById(R.id.topPanel);
        tvTitle = findViewById(R.id.tvPageTitle);
        tvTitleIcon = findViewById(R.id.tvTitleIcon);
        statusBar = findViewById(R.id.layoutStatusBar);
        tvSignalIcon = findViewById(R.id.tvSignalIcon);
        tvBatteryIcon = findViewById(R.id.tvBatteryIcon);
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent);
        contentContainer = findViewById(R.id.midPanel);
        bottomBar = findViewById(R.id.bottomPanel);

        tvSoftLeft = findViewById(R.id.tvSoftLeft);
        if (tvSoftLeft == null) {
            // 兼容 launcher 命名
            int resId = getResources().getIdentifier("bottomLeft", "id", getPackageName());
            if (resId != 0) tvSoftLeft = findViewById(resId);
        }

        tvSoftCenter = findViewById(R.id.tvSoftCenter);
        if (tvSoftCenter == null) {
            int resId = getResources().getIdentifier("bottomCenter", "id", getPackageName());
            if (resId != 0) tvSoftCenter = findViewById(resId);
        }

        tvSoftRight = findViewById(R.id.tvSoftRight);
        if (tvSoftRight == null) {
            int resId = getResources().getIdentifier("bottomRight", "id", getPackageName());
            if (resId != 0) tvSoftRight = findViewById(resId);
        }

        // 时钟 TextView（若布局中存在）
        int timeResId = getResources().getIdentifier("tvTime", "id", getPackageName());
        if (timeResId != 0) {
            tvTime = findViewById(timeResId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvTime != null) {
            clockHandler.post(clockTick);
        }
        // 从其他窗口/桌面返回时恢复持焦，防止 touch mode 导致首键失效
        ensureActiveFocus();
        // 确保前台页面激活时软键栏与标题同步刷新
        refreshPageBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tvTime != null) {
            clockHandler.removeCallbacks(clockTick);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 窗口焦点就绪时精准激活焦点，杜绝系统 leaveTouchMode 吞噬首个物理按键
            ensureActiveFocus();
        }
    }

    /**
     * 保证窗口内永远有 View 持有焦点，杜绝 Android 系统在 Touch Mode 下吞噬首个物理按键
     */
    public void ensureActiveFocus() {
        View target = getCurrentFocus();
        if (target == null) {
            target = contentContainer != null ? contentContainer : (getWindow() != null ? getWindow().getDecorView() : null);
        }
        if (target != null) {
            target.setFocusable(true);
            target.setFocusableInTouchMode(true);
            target.requestFocus();
            final View fTarget = target;
            target.post(() -> {
                if (getCurrentFocus() == null) {
                    fTarget.setFocusable(true);
                    fTarget.setFocusableInTouchMode(true);
                    fTarget.requestFocus();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterBatteryReceiver();
    }

    @LayoutRes
    protected int getContentLayoutRes() {
        return 0;
    }

    protected void onInitViews() {
    }

    public void setTitleText(CharSequence title) {
        if (tvTitle != null) {
            tvTitle.setText(title);
        }
    }

    public void setPageTitle(CharSequence title) {
        setTitleText(title);
    }

    public void setTitleIcon(CharSequence iconCode) {
        if (tvTitleIcon != null) {
            if (iconCode != null && iconCode.length() > 0) {
                NokiaIcons.setIcon(tvTitleIcon, iconCode.toString());
                tvTitleIcon.setVisibility(View.VISIBLE);
            } else {
                tvTitleIcon.setVisibility(View.GONE);
            }
        }
    }

    public void setStatusBarVisible(boolean visible) {
        if (statusBar != null) {
            statusBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setSignalIcon(CharSequence iconCode) {
        if (tvSignalIcon != null && iconCode != null) {
            NokiaIcons.setIcon(tvSignalIcon, iconCode.toString());
        }
    }

    public void setBatteryPercent(CharSequence text) {
        if (tvBatteryPercent != null) {
            tvBatteryPercent.setText(text != null ? text : "");
        }
    }

    /**
     * 注册电量广播，自动更新电池图标与百分比。
     */
    protected void registerBatteryReceiver() {
        if (batteryReceiver != null) return;
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBatteryInfo(intent);
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
        Intent sticky = registerReceiver(batteryReceiver, filter);
        if (sticky != null) {
            updateBatteryInfo(sticky);
        }
    }

    protected void unregisterBatteryReceiver() {
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
            batteryReceiver = null;
        }
    }

    private void updateBatteryInfo(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int percent = -1;
        if (level >= 0 && scale > 0) {
            percent = (int) (level * 100f / scale);
        }
        boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        if (tvBatteryIcon != null && percent >= 0) {
            NokiaBatteryDrawable drawable = null;
            if (tvBatteryIcon.getDrawable() instanceof NokiaBatteryDrawable) {
                drawable = (NokiaBatteryDrawable) tvBatteryIcon.getDrawable();
            }
            if (drawable == null) {
                drawable = new NokiaBatteryDrawable(this);
                tvBatteryIcon.setImageDrawable(drawable);
            }
            drawable.setBatteryState(percent, charging);
        }
        if (tvBatteryPercent != null && percent >= 0) {
            tvBatteryPercent.setText(percent + "%");
        }
    }

    public void setSoftKeys(CharSequence left, CharSequence center, CharSequence right) {
        applySoftKeyText(tvSoftLeft, left, false);
        applySoftKeyText(tvSoftCenter, center, true);
        applySoftKeyText(tvSoftRight, right, false);
    }

    /** 桌面兼容别名 */
    public void setBottomBar(String left, String center, String right) {
        setSoftKeys(left, center, right);
    }

    public void setSoftLeft(CharSequence text) {
        applySoftKeyText(tvSoftLeft, text, false);
    }

    public void setSoftCenter(CharSequence text) {
        applySoftKeyText(tvSoftCenter, text, true);
    }

    public void setSoftRight(CharSequence text) {
        applySoftKeyText(tvSoftRight, text, false);
    }

    public void setBottomCenterText(String text) {
        setSoftCenter(text);
    }

    private void applySoftKeyText(TextView tv, CharSequence text, boolean isCenter) {
        if (tv == null) return;
        if (text == null || text.length() == 0) {
            tv.setText("");
        } else {
            tv.setText(text);
            if (isCenter) {
                fitCenterTextToWidth(tv);
            }
        }
    }

    /**
     * 根据实际可用宽度动态缩小底部中间标题字号（dp 单位），
     * 保证长标题完整显示、不出现省略号截断。
     */
    private void fitCenterTextToWidth(final TextView tv) {
        tv.post(() -> {
            if (tv.getVisibility() != View.VISIBLE || tv.getWidth() <= 0) {
                return;
            }
            final float density = getResources().getDisplayMetrics().density;
            final android.graphics.Paint paint = tv.getPaint();
            while (tv.getTextSize() > 6f * density) {
                if (paint.measureText(tv.getText().toString()) <= tv.getWidth()) {
                    break;
                }
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                        Math.max(6f, tv.getTextSize() / density - 0.5f));
            }
        });
    }

    /**
     * 通用滚动跟随辅助方法：确保目标子视图 {@code target} 完全处于 {@code scroll} 的可视区内。
     */
    public void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target) {
        if (scroll == null || target == null) return;
        scroll.post(() -> {
            if (isDestroyed() || isFinishing()) return;
            int scrollY = scroll.getScrollY();
            int itemTop = 0;
            View current = target;
            while (current != null && current != scroll && current.getParent() instanceof View) {
                itemTop += current.getTop();
                current = (View) current.getParent();
            }
            int itemBottom = itemTop + target.getHeight();
            int svHeight = scroll.getHeight();
            if (svHeight <= 0) return;
            if (itemTop < scrollY) {
                scroll.smoothScrollTo(0, itemTop);
            } else if (itemBottom > scrollY + svHeight) {
                scroll.smoothScrollTo(0, itemBottom - svHeight);
            }
        });
    }

    /**
     * 应用主题到骨架组件。
     */
    public void applyTheme(@NonNull NokiaTheme.ThemeDef theme) {
        if (titleBar != null) {
            titleBar.setBackground(theme.createTitleDrawable());
        }
        if (bottomBar != null) {
            bottomBar.setBackground(theme.createSoftKeyDrawable());
        }
        getWindow().setBackgroundDrawable(new ColorDrawable(theme.darkColor));
        if (contentContainer != null) {
            contentContainer.setBackgroundColor(theme.darkColor);
            if (contentContainer.getChildCount() > 0) {
                View contentRoot = contentContainer.getChildAt(0);
                if (contentRoot != null) {
                    contentRoot.setBackgroundColor(theme.darkColor);
                }
            }
        }
        if (tvTitle != null) tvTitle.setTextColor(theme.textColor);
        if (tvTitleIcon != null) tvTitleIcon.setTextColor(theme.textColor);
        if (tvSoftLeft != null) tvSoftLeft.setTextColor(theme.textColor);
        if (tvSoftCenter != null) tvSoftCenter.setTextColor(theme.textColor);
        if (tvSoftRight != null) tvSoftRight.setTextColor(theme.textColor);
    }

    /**
     * {@link KeyResolver} 默认实现：兜底使用标准 Android 键码。
     * key-core 子类可重写以委托 {@code NokiaClient}。
     */
    @Override
    public int resolveAction(@NonNull KeyEvent event) {
        if (event == null) return NokiaKeyAction.UNKNOWN;
        return DefaultKeyResolver.INSTANCE.resolveAction(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        long now = SystemClock.uptimeMillis();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == lastDownKeyCode && (now - lastDownTime) < DEBOUNCE_MS && event.getRepeatCount() == 0) {
                return true;
            }
            lastDownKeyCode = keyCode;
            lastDownTime = now;

            int action = resolveAction(event);
            NokiaLog.d(TAG, "dispatchKeyEvent keyCode=" + keyCode
                    + " repeat=" + event.getRepeatCount()
                    + " resolvedAction=" + action);
            if (action >= 0) {
                lastDownHandled = onAction(action);
                if (lastDownHandled) {
                    return true;
                }
            } else {
                lastDownHandled = false;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (keyCode == lastDownKeyCode && lastDownHandled) {
                lastDownKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                lastDownHandled = false;
                return true;
            }
            lastDownKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            lastDownHandled = false;
        }

        return super.dispatchKeyEvent(event);
    }

    /**
     * 获取当前前台活跃的页面契约（Fragment 或 View）。
     */
    @Nullable
    protected NokiaPage getCurrentPage() {
        if (this instanceof NokiaPage) {
            return (NokiaPage) this;
        }
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.Fragment primaryNav = fm.getPrimaryNavigationFragment();
        if (primaryNav instanceof NokiaPage && primaryNav.isAdded() && !primaryNav.isHidden()) {
            return (NokiaPage) primaryNav;
        }
        java.util.List<androidx.fragment.app.Fragment> fragments = fm.getFragments();
        for (int i = fragments.size() - 1; i >= 0; i--) {
            androidx.fragment.app.Fragment f = fragments.get(i);
            if (f != null && f.isAdded() && !f.isHidden() && f instanceof NokiaPage) {
                return (NokiaPage) f;
            }
        }
        return null;
    }

    @Override
    public void refreshPageBar() {
        NokiaPage page = getCurrentPage();
        if (page != null) {
            CharSequence title = page.getPageTitle();
            if (title != null) {
                setPageTitle(title);
            }
            setSoftKeys(page.getSoftLeftText(), page.getSoftCenterText(), page.getSoftRightText());
        }
    }

    @Override
    public void exitCurrent() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            finish();
        }
    }

    @Override
    public void openFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.midPanel, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onBackPressed() {
        NokiaPage page = getCurrentPage();
        if (page != null && page.onBack()) {
            return;
        }
        exitCurrent();
    }

    @CallSuper
    protected boolean onAction(int action) {
        NokiaPage page = getCurrentPage();
        if (page != null) {
            boolean handled = false;
            switch (action) {
                case NokiaKeyAction.UP:
                case NokiaKeyAction.DOWN:
                case NokiaKeyAction.LEFT:
                case NokiaKeyAction.RIGHT:
                    handled = page.onDirection(action);
                    break;
                case NokiaKeyAction.SELECT:
                    handled = page.onSelect();
                    break;
                case NokiaKeyAction.SOFT_LEFT:
                    handled = page.onSoftLeft();
                    break;
                case NokiaKeyAction.SOFT_RIGHT:
                    handled = page.onSoftRight();
                    break;
            }
            if (handled) {
                return true;
            }
        }

        if (action == NokiaKeyAction.SOFT_RIGHT) {
            finish();
            return true;
        }
        return false;
    }
}
