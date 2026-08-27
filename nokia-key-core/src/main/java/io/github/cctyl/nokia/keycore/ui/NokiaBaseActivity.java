package io.github.cctyl.nokia.keycore.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.NokiaBatteryDrawable;
import io.github.cctyl.nokia.common.ui.NokiaIcons;
import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.common.ui.focus.NokiaFocusHost;
import io.github.cctyl.nokia.common.ui.page.NokiaPage;
import io.github.cctyl.nokia.common.ui.page.NokiaPageHost;
import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 诺基亚复古风格基类 Activity。
 * 封装 240dp 基准复古骨架、标题栏、三段式软键条、主题/字体自动应用与按键分发。
 */
public abstract class NokiaBaseActivity extends AppCompatActivity implements NokiaClient.OnConfigChangedListener, NokiaPageHost {

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

    private int lastDownKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long lastDownTime = 0;
    private boolean lastDownHandled = false;

    private BroadcastReceiver batteryReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nokia_base);
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
        tvSoftCenter = findViewById(R.id.tvSoftCenter);
        tvSoftRight = findViewById(R.id.tvSoftRight);

        // 兜底全屏：legacy systemUiVisibility（在部分设备/ROM 上比 InsetsController 更稳）
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        int contentRes = getContentLayoutRes();
        if (contentRes != 0) {
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

        // 注册全局配置监听
        NokiaClient.get(this).addListener(this);

        // 主动触发一次主题与字体应用
        NokiaTheme.ThemeDef currentTheme = NokiaTheme.getTheme(NokiaClient.get(this).getCurrentThemeId());
        if (currentTheme != null) {
            onThemeChanged(NokiaClient.get(this).getCurrentThemeId(), currentTheme);
        }
        onFontChanged(NokiaClient.get(this).getCurrentFontId(), NokiaClient.get(this).getCurrentFontScale());

        // 确保内容容器具备在 touch mode 下持焦能力
        if (contentContainer != null) {
            contentContainer.setFocusable(true);
            contentContainer.setFocusableInTouchMode(true);
        }
        ensureActiveFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从其他窗口/桌面返回时恢复持焦，防止 touch mode 导致首键失效
        ensureActiveFocus();
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
        NokiaClient.get(this).removeListener(this);
    }

    @LayoutRes
    protected abstract int getContentLayoutRes();

    protected abstract void onInitViews();

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
     * 在 onCreate 末尾调用。
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
        // 立即用 sticky broadcast 刷新一次
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
        // 电池图标：使用 NokiaBatteryDrawable 纯矢量绘制
        if (tvBatteryIcon != null && percent >= 0) {
            NokiaBatteryDrawable drawable = (NokiaBatteryDrawable) tvBatteryIcon.getDrawable();
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
        if (tvSoftLeft != null) tvSoftLeft.setText(left != null ? left : "");
        if (tvSoftCenter != null) tvSoftCenter.setText(center != null ? center : "");
        if (tvSoftRight != null) tvSoftRight.setText(right != null ? right : "");
    }

    public void setSoftLeft(CharSequence text) {
        if (tvSoftLeft != null) tvSoftLeft.setText(text != null ? text : "");
    }

    public void setSoftCenter(CharSequence text) {
        if (tvSoftCenter != null) tvSoftCenter.setText(text != null ? text : "");
    }

    public void setSoftRight(CharSequence text) {
        if (tvSoftRight != null) tvSoftRight.setText(text != null ? text : "");
    }

    /**
     * 通用滚动跟随辅助方法：确保目标子视图 {@code target} 完全处于 {@code scroll} 的可视区内。
     * <p>
     * 支持单层列表与任意多层嵌套容器（例如网格或嵌套 LinearLayout），
     * 自动循环累加父级视图的 top 偏移量，计算出相对于 ScrollView 的真实垂直坐标并平滑滚动。
     *
     * @param scroll 滚动容器
     * @param target 需要滚入可视区的子视图
     */
    public void smoothScrollToVisible(@Nullable android.widget.ScrollView scroll, @Nullable View target) {
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

    @Override
    public void onKeysChanged(@NonNull NokiaKeyBinding binding, @NonNull NokiaClient.ConfigSource source) {
        // 供子类按需重写
    }

    @Override
    public void onThemeChanged(@NonNull String themeId, @NonNull NokiaTheme.ThemeDef theme) {
        // 自动将当前主题应用到标题栏与底部软键
        if (titleBar != null) {
            titleBar.setBackground(theme.createTitleDrawable());
        }
        if (bottomBar != null) {
            bottomBar.setBackground(theme.createSoftKeyDrawable());
        }
        // 窗口背景 + 内容区 + 业务内容根 统一跟随主题深色，确保深色 Nokia 风格贴合（主题可切换）
        getWindow().setBackgroundDrawable(new ColorDrawable(theme.darkColor));
        if (contentContainer != null) {
            contentContainer.setBackgroundColor(theme.darkColor);
            // 业务 inflate 进来的内容根也设为深色，防止业务布局透明时仍透出浅色
            if (contentContainer.getChildCount() > 0) {
                View contentRoot = contentContainer.getChildAt(0);
                if (contentRoot != null) {
                    contentRoot.setBackgroundColor(theme.darkColor);
                }
            }
        }
        if (tvTitle != null) {
            tvTitle.setTextColor(theme.textColor);
        }
        if (tvTitleIcon != null) {
            tvTitleIcon.setTextColor(theme.textColor);
        }
        if (tvSoftLeft != null) tvSoftLeft.setTextColor(theme.textColor);
        if (tvSoftCenter != null) tvSoftCenter.setTextColor(theme.textColor);
        if (tvSoftRight != null) tvSoftRight.setTextColor(theme.textColor);
    }

    @Override
    public void onFontChanged(@NonNull String fontId, float fontScale) {
        // 自动将点阵字体与缩放应用到整棵 View 树（包含 DecorView，涵盖标题栏、内容区、软键条）
        if (getWindow() != null && getWindow().getDecorView() != null) {
            NokiaFontManager.applyToViewTree(getWindow().getDecorView());
        } else if (rootContainer != null) {
            NokiaFontManager.applyToViewTree(rootContainer);
        }
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

            int action = NokiaClient.get(this).getKeyBinding().resolveAction(keyCode);
            Log.d(TAG, "dispatchKeyEvent keyCode=" + keyCode
                    + " repeat=" + event.getRepeatCount()
                    + " resolvedAction=" + action
                    + " source=" + NokiaClient.get(this).getConfigSource());
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
     * 默认尝试从 SupportFragmentManager 获取当前可见的 Fragment；子类亦可重写返回自定义页面对象。
     */
    @Nullable
    protected NokiaPage getCurrentPage() {
        if (this instanceof NokiaPage) {
            return (NokiaPage) this;
        }
        for (androidx.fragment.app.Fragment f : getSupportFragmentManager().getFragments()) {
            if (f != null && f.isVisible() && f instanceof NokiaPage) {
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
            onBackPressed();
        }
    }

    @Override
    public void onBackPressed() {
        NokiaPage page = getCurrentPage();
        if (page != null && page.onBack()) {
            return;
        }
        super.onBackPressed();
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
