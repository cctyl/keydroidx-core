package io.github.cctyl.nokia.keycore.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.graphics.drawable.GradientDrawable;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;

/**
 * 全屏复古文本输入页（供表单行点击后进入，如反馈页的联系方式/问题描述）。
 *
 * <p>大尺寸输入区、初始即聚焦光标，物理键盘直接输入；
 * 软键：LSK=确定（返回结果），RSK=返回（取消）。</p>
 *
 * <p>用法：</p>
 * <pre>
 * Intent it = new Intent(this, NokiaTextInputActivity.class)
 *         .putExtra(NokiaTextInputActivity.EXTRA_TITLE, "问题描述")
 *         .putExtra(NokiaTextInputActivity.EXTRA_HINT, "描述问题与复现步骤")
 *         .putExtra(NokiaTextInputActivity.EXTRA_TEXT, currentText)
 *         .putExtra(NokiaTextInputActivity.EXTRA_MAX_CHARS, 500);
 * startActivityForResult(it, REQUEST_CODE);
 * </pre>
 * 结果通过 {@link #RESULT_TEXT} 返回；取消时 resultCode 为 RESULT_CANCELED。
 */
public class NokiaTextInputActivity extends NokiaBaseActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_HINT = "hint";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MAX_CHARS = "maxChars";

    public static final String RESULT_TEXT = "resultText";

    private EditText editInput;
    private boolean confirmed = false;

    @Override
    protected int getContentLayoutRes() {
        return R.layout.activity_nokia_text_input;
    }

    @Override
    protected void onInitViews() {
        Intent it = getIntent();
        applyTheme();
        String title = it.getStringExtra(EXTRA_TITLE);
        setPageTitle(title == null ? "输入" : title);
        setSoftKeys("确定", "", "返回");

        editInput = findViewById(R.id.editInput);

        String hint = it.getStringExtra(EXTRA_HINT);
        if (hint != null) editInput.setHint(hint);

        String text = it.getStringExtra(EXTRA_TEXT);
        if (text != null && text.length() > 0) {
            editInput.setText(text);
            editInput.setSelection(text.length());
        }

        int max = it.getIntExtra(EXTRA_MAX_CHARS, 500);
        editInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});

        // 初始即聚焦，物理键盘可直接输入
        editInput.requestFocus();
    }

    /** 输入区按当前主题着色 */
    private void applyTheme() {
        NokiaTheme.ThemeDef theme = NokiaClient.get(this).getCurrentTheme();
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.primaryColor, theme.darkColor});
        editInput.setBackground(bg);
        editInput.setTextColor(theme.textColor);
        editInput.setHintTextColor(theme.subTextColor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        editInput.requestFocus();
    }

    /**
     * 输入页对导航键透明：所有按键直接交给 EditText 处理
     * （方向键移动光标、回车换行），仅软键由本页消费。
     */
    @Override
    protected boolean onAction(int action) {
        switch (action) {
            case NokiaKeyAction.SOFT_LEFT:
                confirm();
                return true;
            case NokiaKeyAction.SOFT_RIGHT:
                cancel();
                return true;
            default:
                return false; // 其余按键透传给 EditText
        }
    }

    private void confirm() {
        String text = editInput.getText().toString().trim();
        if (text.length() == 0) {
            Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        confirmed = true;
        Intent data = new Intent();
        data.putExtra(RESULT_TEXT, text);
        setResult(RESULT_OK, data);
        finish();
    }

    private void cancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void finish() {
        if (!confirmed && !isFinishing()) {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }
}
