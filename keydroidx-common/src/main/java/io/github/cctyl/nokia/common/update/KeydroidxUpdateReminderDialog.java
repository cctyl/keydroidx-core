package io.github.cctyl.nokia.common.update;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.model.KeyResolver;
import io.github.cctyl.nokia.common.model.KeydroidxKeyAction;
import io.github.cctyl.nokia.common.ui.KeydroidxFontManager;
import io.github.cctyl.nokia.common.ui.KeydroidxTheme;
import io.github.cctyl.nokia.common.ui.KeydroidxUi;
import io.github.cctyl.nokia.common.ui.focus.KeydroidxDialogFocus;

/**
 * 紧凑版复古诺基亚风格「发现新版本」提醒弹窗。
 *
 * <p>与 {@code KeydroidxOptionsDialog} 同级的紧凑高度（标题栏 + 固定 96dp 可滚动
 * 内容区 + 底部软键栏），更新说明较长时在内容区内滚动，不会把软键栏挤出屏幕。</p>
 *
 * <p>按键（遵循 FEATURE_PHONE_UI_SPEC：LSK/CENTER=确认、RSK=取消/返回）：</p>
 * <ul>
 *   <li>LSK / CENTER：主操作（如「更新」）；</li>
 *   <li>RSK：副操作（如「忽略此版本」/「取消」）；</li>
 *   <li>UP / DOWN：滚动更新说明；</li>
 *   <li>BACK：仅关闭（由调用方决定是否保留「待提醒」记录）。</li>
 * </ul>
 *
 * <p>内容区为纯展示（无焦点项），符合规范 6.1 对纯信息屏的豁免。</p>
 */
public class KeydroidxUpdateReminderDialog extends Dialog {

    /** LSK / CENTER 主操作回调 */
    public interface OnActionListener {
        void onAction();
    }

    private final String title;
    private final String message;
    private String positiveText = "更新";
    private String negativeText = "忽略此版本";
    private OnActionListener positiveListener;
    private OnActionListener negativeListener;

    private ScrollView bodyScroll;

    public KeydroidxUpdateReminderDialog(@NonNull Context context,
                                         @NonNull String title, @NonNull String message) {
        super(context, R.style.Theme_Keydroidx_Dialog);
        this.title = title;
        this.message = message;
    }

    public KeydroidxUpdateReminderDialog setPositiveButton(String text, OnActionListener listener) {
        this.positiveText = text;
        this.positiveListener = listener;
        return this;
    }

    public KeydroidxUpdateReminderDialog setNegativeButton(String text, OnActionListener listener) {
        this.negativeText = text;
        this.negativeListener = listener;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_keydroidx_update_reminder);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        KeydroidxTheme.ThemeDef currentTheme = KeydroidxUi.getTheme(getContext());
        View titleBar = findViewById(R.id.dialogTitleBar);
        if (titleBar != null) {
            titleBar.setBackground(currentTheme.createTitleDrawable());
        }
        bodyScroll = findViewById(R.id.dialogBody);
        if (bodyScroll != null) {
            bodyScroll.setBackground(currentTheme.createDialogBodyDrawable());
        }
        View bottomBar = findViewById(R.id.dialogBottomBar);
        if (bottomBar != null) {
            bottomBar.setBackground(currentTheme.createSoftKeyDrawable());
        }

        TextView tvTitle = findViewById(R.id.dialogTitle);
        if (tvTitle != null) {
            tvTitle.setText(title);
            tvTitle.setTextColor(currentTheme.textColor);
        }
        TextView tvMessage = findViewById(R.id.dialogMessage);
        if (tvMessage != null) {
            tvMessage.setText(message);
            tvMessage.setTextColor(currentTheme.textColor);
        }

        TextView btnLeft = findViewById(R.id.softLeft);
        if (btnLeft != null) {
            btnLeft.setText(positiveText);
            btnLeft.setTextColor(currentTheme.textColor);
            btnLeft.setOnClickListener(v -> handlePositive());
        }
        TextView btnRight = findViewById(R.id.softRight);
        if (btnRight != null) {
            btnRight.setText(negativeText);
            btnRight.setTextColor(currentTheme.textColor);
            btnRight.setOnClickListener(v -> handleNegative());
        }
    }

    private void handlePositive() {
        dismiss();
        if (positiveListener != null) {
            positiveListener.onAction();
        }
    }

    private void handleNegative() {
        dismiss();
        if (negativeListener != null) {
            negativeListener.onAction();
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int action = KeydroidxUi.getKeyResolver(getContext()).resolveAction(event);
            if (action == KeydroidxKeyAction.SOFT_LEFT || action == KeydroidxKeyAction.SELECT) {
                handlePositive();
                return true;
            } else if (action == KeydroidxKeyAction.SOFT_RIGHT) {
                handleNegative();
                return true;
            } else if (action == KeydroidxKeyAction.UP || action == KeydroidxKeyAction.DOWN) {
                scrollBody(action == KeydroidxKeyAction.DOWN);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** UP/DOWN 滚动内容区（一次约一屏的 60%，遵循「视口跟随内容」的滚动方式）。 */
    private void scrollBody(boolean down) {
        if (bodyScroll == null) {
            return;
        }
        int delta = (int) (bodyScroll.getHeight() * 0.6f);
        if (delta <= 0) {
            delta = 60;
        }
        bodyScroll.smoothScrollBy(0, down ? delta : -delta);
    }

    @Override
    public void show() {
        super.show();
        KeydroidxDialogFocus.forceNonTouchMode(this);
        if (getWindow() != null && getWindow().getDecorView() != null) {
            KeydroidxFontManager.applyToViewTree(getWindow().getDecorView());
        }
    }
}
