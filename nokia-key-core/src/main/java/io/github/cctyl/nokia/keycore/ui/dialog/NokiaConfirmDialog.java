package io.github.cctyl.nokia.keycore.ui.dialog;

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

import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.ui.NokiaFontManager;

/**
 * 标准复古诺基亚风格「确认 / 提示」弹窗（开箱即用 UI 组件）。
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
        super(context, R.style.Theme_NokiaKeyCore_Dialog);
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
        TextView tvTitle = findViewById(R.id.dialogTitle);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        TextView tvMsg = findViewById(R.id.dialogMessage);
        if (tvMsg != null) {
            tvMsg.setText(message);
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

        // 应用主题
        NokiaTheme.ThemeDef currentTheme = NokiaTheme.getTheme(NokiaClient.get(getContext()).getCurrentThemeId());
        View titleBar = findViewById(R.id.dialogTitleBar);
        if (titleBar != null && currentTheme != null) {
            titleBar.setBackground(currentTheme.createTitleDrawable());
        }
        View dialogBody = findViewById(R.id.dialogBody);
        if (dialogBody != null && currentTheme != null) {
            dialogBody.setBackgroundColor(currentTheme.cardBgColor);
        }
        View bottomBar = findViewById(R.id.dialogBottomBar);
        if (bottomBar != null && currentTheme != null) {
            bottomBar.setBackground(currentTheme.createSoftKeyDrawable());
        }
        if (btnLeft != null && currentTheme != null) {
            btnLeft.setTextColor(currentTheme.textColor);
        }
        if (btnRight != null && currentTheme != null) {
            btnRight.setTextColor(currentTheme.textColor);
        }
        if (tvTitle != null && currentTheme != null) {
            tvTitle.setTextColor(currentTheme.textColor);
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
            int action = NokiaKeyClient.get(getContext()).getBinding().resolveAction(event);
            if (action == NokiaKeyAction.ACTION_SOFT_LEFT || action == NokiaKeyAction.ACTION_SELECT) {
                handleConfirm();
                return true;
            } else if (action == NokiaKeyAction.ACTION_SOFT_RIGHT) {
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
