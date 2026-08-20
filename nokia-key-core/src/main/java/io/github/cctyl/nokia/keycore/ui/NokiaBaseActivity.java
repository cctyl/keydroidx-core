package io.github.cctyl.nokia.keycore.ui;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 诺基亚复古风格基类 Activity。
 * 封装 240dp 基准复古骨架、标题栏、三段式软键条、主题/字体自动应用与按键分发。
 */
public abstract class NokiaBaseActivity extends AppCompatActivity implements NokiaClient.OnConfigChangedListener {

    private static final long DEBOUNCE_MS = 60;

    protected View rootContainer;
    protected View titleBar;
    protected TextView tvTitle;
    protected FrameLayout contentContainer;
    protected View bottomBar;
    protected TextView tvSoftLeft;
    protected TextView tvSoftCenter;
    protected TextView tvSoftRight;

    private int lastDownKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long lastDownTime = 0;
    private boolean lastDownHandled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nokia_base);

        rootContainer = findViewById(R.id.midPanel);
        titleBar = findViewById(R.id.topPanel);
        tvTitle = findViewById(R.id.tvPageTitle);
        contentContainer = findViewById(R.id.midPanel);
        bottomBar = findViewById(R.id.bottomPanel);
        tvSoftLeft = findViewById(R.id.tvSoftLeft);
        tvSoftCenter = findViewById(R.id.tvSoftCenter);
        tvSoftRight = findViewById(R.id.tvSoftRight);

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        if (tvTitle != null) {
            tvTitle.setTextColor(theme.textColor);
        }
        if (tvSoftLeft != null) tvSoftLeft.setTextColor(theme.textColor);
        if (tvSoftCenter != null) tvSoftCenter.setTextColor(theme.textColor);
        if (tvSoftRight != null) tvSoftRight.setTextColor(theme.textColor);
    }

    @Override
    public void onFontChanged(@NonNull String fontId, float fontScale) {
        // 自动将点阵字体应用到整个 View 树
        if (rootContainer != null) {
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

    @CallSuper
    protected boolean onAction(int action) {
        if (action == NokiaKeyAction.SOFT_RIGHT) {
            finish();
            return true;
        }
        return false;
    }
}
