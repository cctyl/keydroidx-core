package io.github.cctyl.nokia.common.ui.dialog;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.model.KeyResolver;
import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.NokiaFontManager;
import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.common.ui.NokiaUi;
import io.github.cctyl.nokia.common.ui.focus.NokiaDialogFocus;
import io.github.cctyl.nokia.common.R;

/**
 * 标准备复古诺基亚风格「确认 / 提示」弹窗（通用 UI 组件）。
 *
 * <p>属于 {@code nokia-common}，零业务依赖：按键解析与主题均从宿主
 * {@link Context} 获取，宿主未实现时回退到标准映射与默认主题。</p>
 */
public class NokiaConfirmDialog extends Dialog {

    public interface OnConfirmListener {
        void onConfirm();
    }

    private final String title;
    private final String message;
    private String positiveText = "确认";
    private String negativeText = "取消";
    private OnConfirmListener confirmListener;
    private Runnable cancelListener;

    public NokiaConfirmDialog(@NonNull Context context, @NonNull String title, @NonNull String message) {
        super(context, R.style.Theme_Nokia_Dialog);
        this.title = title;
        this.message = message;
    }

    public NokiaConfirmDialog setPositiveButton(String text, OnConfirmListener listener) {
        this.positiveText = text;
        this.confirmListener = listener;
        return this;
    }

    public NokiaConfirmDialog setNegativeButton(String text, @Nullable Runnable listener) {
        this.negativeText = text;
        this.cancelListener = listener;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_nokia_confirm);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        initViews();
    }

    private void initViews() {
        NokiaTheme.ThemeDef currentTheme = NokiaUi.getTheme(getContext());

        View titleBar = findViewById(R.id.dialogTitleBar);
        if (titleBar != null) {
            titleBar.setBackground(currentTheme.createTitleDrawable());
        }
        View dialogBody = findViewById(R.id.dialogBody);
        if (dialogBody != null) {
            dialogBody.setBackgroundColor(currentTheme.cardBgColor);
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
            btnLeft.setOnClickListener(v -> handleConfirm());
        }

        TextView btnRight = findViewById(R.id.softRight);
        if (btnRight != null) {
            btnRight.setText(negativeText);
            btnRight.setOnClickListener(v -> handleCancel());
        }
    }

    private void handleConfirm() {
        dismiss();
        if (confirmListener != null) {
            confirmListener.onConfirm();
        }
    }

    private void handleCancel() {
        dismiss();
        if (cancelListener != null) {
            cancelListener.run();
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int action = NokiaUi.getKeyResolver(getContext()).resolveAction(event);
            if (action == NokiaKeyAction.SOFT_LEFT || action == NokiaKeyAction.SELECT) {
                handleConfirm();
                return true;
            } else if (action == NokiaKeyAction.SOFT_RIGHT) {
                handleCancel();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void show() {
        super.show();
        NokiaDialogFocus.forceNonTouchMode(this);
        if (getWindow() != null && getWindow().getDecorView() != null) {
            NokiaFontManager.applyToViewTree(getWindow().getDecorView());
        }
    }
}
