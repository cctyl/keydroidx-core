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
import android.widget.EditText;
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
 * 标准备复古诺基亚风格「文本输入」弹窗（通用 UI 组件）。
 *
 * <p>属于 {@code nokia-common}，零业务依赖。</p>
 */
public class NokiaInputDialog extends Dialog {

    public interface OnInputConfirmListener {
        void onConfirm(String text);
    }

    private final String title;
    private final String defaultText;
    private final String hint;
    private EditText editInput;
    private OnInputConfirmListener listener;
    private boolean multiline;
    private int maxChars;

    public NokiaInputDialog(@NonNull Context context, @NonNull String title, @Nullable String defaultText, @Nullable String hint) {
        this(context, title, defaultText, hint, false, 0);
    }

    public NokiaInputDialog(@NonNull Context context, @NonNull String title, @Nullable String defaultText,
                            @Nullable String hint, boolean multiline, int maxChars) {
        super(context, R.style.Theme_Nokia_Dialog);
        this.title = title;
        this.defaultText = defaultText != null ? defaultText : "";
        this.hint = hint != null ? hint : "";
        this.multiline = multiline;
        this.maxChars = maxChars;
    }

    public NokiaInputDialog setOnInputConfirmListener(OnInputConfirmListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_nokia_input);

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
            dialogBody.setBackground(currentTheme.createDialogBodyDrawable());
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

        editInput = findViewById(R.id.dialogInput);
        if (editInput != null) {
            editInput.setBackground(currentTheme.createInputFieldDrawable(
                    io.github.cctyl.nokia.common.util.NokiaDimens.dp(getContext().getResources(), 1),
                    io.github.cctyl.nokia.common.util.NokiaDimens.dp(getContext().getResources(), 3)));
            editInput.setTextColor(currentTheme.textColor);
            editInput.setHintTextColor(currentTheme.subTextColor);
            editInput.setText(defaultText);
            editInput.setHint(hint);
            if (multiline) {
                editInput.setSingleLine(false);
                editInput.setMinLines(3);
                editInput.setMaxLines(6);
            }
            if (maxChars > 0) {
                editInput.setFilters(new android.text.InputFilter[]{
                        new android.text.InputFilter.LengthFilter(maxChars)});
            }
            editInput.setSelection(editInput.getText().length());
        }

        TextView btnLeft = findViewById(R.id.softLeft);
        if (btnLeft != null) {
            btnLeft.setTextColor(currentTheme.textColor);
            btnLeft.setOnClickListener(v -> handleConfirm());
        }

        TextView btnRight = findViewById(R.id.softRight);
        if (btnRight != null) {
            btnRight.setTextColor(currentTheme.textColor);
            btnRight.setOnClickListener(v -> dismiss());
        }
    }

    private void handleConfirm() {
        String result = editInput != null ? editInput.getText().toString() : "";
        dismiss();
        if (listener != null) {
            listener.onConfirm(result);
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int action = NokiaUi.getKeyResolver(getContext()).resolveAction(event);
            if (action == NokiaKeyAction.SOFT_LEFT || (!multiline && action == NokiaKeyAction.SELECT)) {
                handleConfirm();
                return true;
            } else if (action == NokiaKeyAction.SOFT_RIGHT) {
                dismiss();
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
