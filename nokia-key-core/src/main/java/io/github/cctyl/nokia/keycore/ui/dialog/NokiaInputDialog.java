package io.github.cctyl.nokia.keycore.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;

/**
 * 标准复古诺基亚风格「文本输入」弹窗（开箱即用 UI 组件）。
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

    public NokiaInputDialog(@NonNull Context context, @NonNull String title, @Nullable String defaultText, @Nullable String hint) {
        super(context, R.style.Theme_NokiaKeyCore_Dialog);
        this.title = title;
        this.defaultText = defaultText != null ? defaultText : "";
        this.hint = hint != null ? hint : "";
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
        TextView tvTitle = findViewById(R.id.dialogTitle);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        editInput = findViewById(R.id.dialogInput);
        if (editInput != null) {
            editInput.setText(defaultText);
            editInput.setHint(hint);
            editInput.setSelection(editInput.getText().length());
        }

        TextView btnLeft = findViewById(R.id.softLeft);
        if (btnLeft != null) {
            btnLeft.setOnClickListener(v -> handleConfirm());
        }

        TextView btnRight = findViewById(R.id.softRight);
        if (btnRight != null) {
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
            int action = NokiaKeyClient.get(getContext()).getBinding().resolveAction(event);
            if (action == NokiaKeyAction.ACTION_SOFT_LEFT) {
                handleConfirm();
                return true;
            } else if (action == NokiaKeyAction.ACTION_SOFT_RIGHT) {
                dismiss();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
