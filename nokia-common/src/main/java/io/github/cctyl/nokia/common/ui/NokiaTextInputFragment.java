package io.github.cctyl.nokia.common.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.ui.page.NokiaPageFragment;

/**
 * 诺基亚风格「全屏文本编辑页」（生态通用组件，FEATURE_PHONE_UI_SPEC §19/§20）。
 *
 * <p>功能机（S40 / KaiOS）输入长文本的经典范式：进入全屏编辑页，输入区占据整个内容区，
 * 标题栏与软键条由宿主 {@link NokiaBaseActivity} 骨架固定在屏幕上下两端，
 * 软键条恒定可见（不会出现弹窗挤压软键条导致用户看不见「确定/取消」的问题）。</p>
 *
 * <h3>与已废弃的 {@code NokiaInputDialog} 的区别</h3>
 * <ul>
 *   <li>底部弹窗（Dialog）→ 全屏页面（Fragment，压入返回栈）；</li>
 *   <li>输入区高度 33px → 占满整个内容区；</li>
 *   <li>软键条被压成 0×0 → 由宿主骨架绘制，恒定可见。</li>
 * </ul>
 *
 * <h3>用法（宿主 push 到 midPanel）</h3>
 * <pre>
 * NokiaTextInputFragment page = NokiaTextInputFragment.newInstance(
 *         "问题描述", comment, "描述问题与复现步骤", true, 500);
 * page.setOnConfirmListener(text -&gt; { comment = text; });
 * getSupportFragmentManager().beginTransaction()
 *         .replace(R.id.midPanel, page)
 *         .addToBackStack(null)
 *         .commit();
 * </pre>
 *
 * <h3>按键映射</h3>
 * <ul>
 *   <li>方向键 / 输入键：全部透传给 EditText（移动光标、换行）；</li>
 *   <li>LSK：确定，校验后回传结果并出栈；</li>
 *   <li>RSK / BACK：返回，放弃修改并出栈。</li>
 * </ul>
 */
public class NokiaTextInputFragment extends NokiaPageFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_TEXT = "text";
    private static final String ARG_HINT = "hint";
    private static final String ARG_MULTILINE = "multiline";
    private static final String ARG_MAX_CHARS = "maxChars";

    /** 结果回调：用户按 LSK 确定时触发 */
    public interface OnConfirmListener {
        void onConfirm(String text);
    }

    private EditText editInput;
    private TextView tvHint;
    private TextView tvCounter;

    private String title = "输入";
    private String hint = "";
    private boolean multiline = false;
    private int maxChars = 0;
    private boolean required = true;

    private OnConfirmListener confirmListener;

    public NokiaTextInputFragment() {
        // Fragment 必须保留无参构造
    }

    /**
     * 创建全屏编辑页。
     *
     * @param title     页面标题（显示在顶栏）
     * @param text      初始文本（可为空）
     * @param hint      输入框提示语
     * @param multiline 是否多行输入（多行时禁用 LSK 以外的确认键拦截）
     * @param maxChars  最大字符数，0 表示不限制
     */
    public static NokiaTextInputFragment newInstance(@NonNull String title, @Nullable String text,
                                                     @Nullable String hint, boolean multiline, int maxChars) {
        NokiaTextInputFragment f = new NokiaTextInputFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_TEXT, text != null ? text : "");
        args.putString(ARG_HINT, hint != null ? hint : "");
        args.putBoolean(ARG_MULTILINE, multiline);
        args.putInt(ARG_MAX_CHARS, maxChars);
        f.setArguments(args);
        return f;
    }

    public NokiaTextInputFragment setOnConfirmListener(@Nullable OnConfirmListener listener) {
        this.confirmListener = listener;
        return this;
    }

    /** 设置是否必填。必填时内容为空会提示而非直接返回。默认 true。 */
    public NokiaTextInputFragment setRequired(boolean required) {
        this.required = required;
        return this;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_nokia_text_input;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            title = args.getString(ARG_TITLE, "输入");
            hint = args.getString(ARG_HINT, "");
            multiline = args.getBoolean(ARG_MULTILINE, false);
            maxChars = args.getInt(ARG_MAX_CHARS, 0);
        }
    }

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        editInput = view.findViewById(R.id.editInput);
        tvHint = view.findViewById(R.id.tvHint);
        tvCounter = view.findViewById(R.id.tvCounter);

        String initialText = getArguments() != null ? getArguments().getString(ARG_TEXT, "") : "";

        editInput.setHint(hint);
        editInput.setText(initialText);
        editInput.setSelection(initialText.length());

        // 单行模式下回车键直接确认；多行模式下回车换行
        if (!multiline) {
            editInput.setSingleLine(true);
            editInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    handleConfirm();
                    return true;
                }
                return false;
            });
        } else {
            editInput.setSingleLine(false);
            editInput.setMinLines(6);
            editInput.setImeOptions(EditorInfo.IME_ACTION_NONE);
        }

        if (maxChars > 0) {
            editInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxChars)});
        }

        editInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCounter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (tvHint != null) {
            tvHint.setText(hint);
        }
        updateCounter();
        applyTheme();

        // 初始即聚焦，物理键盘可直接输入
        editInput.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyTheme();
        if (editInput != null) {
            editInput.requestFocus();
        }
    }

    private void updateCounter() {
        if (tvCounter == null) return;
        int len = editInput != null ? editInput.getText().length() : 0;
        tvCounter.setText(maxChars > 0 ? len + "/" + maxChars : String.valueOf(len));
    }

    private void applyTheme() {
        NokiaTheme.ThemeDef theme = NokiaUi.getTheme(requireContext());
        if (theme == null) return;
        View root = getView();
        if (root != null) {
            root.setBackgroundColor(theme.darkColor);
        }
        if (editInput != null) {
            float density = getResources().getDisplayMetrics().density;
            editInput.setBackground(theme.createInputFieldDrawable(1 * density, 3 * density));
            editInput.setTextColor(theme.textColor);
            editInput.setHintTextColor(theme.subTextColor);
        }
        if (tvHint != null) tvHint.setTextColor(theme.subTextColor);
        if (tvCounter != null) tvCounter.setTextColor(theme.subTextColor);
    }

    // ---------- NokiaPage 契约 ----------

    @Override
    public CharSequence getPageTitle() {
        return title;
    }

    @Override
    public CharSequence getSoftLeftText() {
        return "确定";
    }

    @Override
    public CharSequence getSoftCenterText() {
        return "";
    }

    @Override
    public CharSequence getSoftRightText() {
        return "返回";
    }

    /**
     * 方向键全部透传给 EditText（移动光标），不做行焦点导航。
     */
    @Override
    public boolean onDirection(int direction) {
        return false;
    }

    @Override
    public boolean onSelect() {
        // 单行模式下 CENTER 也可确认；多行模式下 CENTER 不改变行为（避免误触退出编辑）
        if (!multiline) {
            handleConfirm();
            return true;
        }
        return false;
    }

    @Override
    public boolean onSoftLeft() {
        handleConfirm();
        return true;
    }

    @Override
    public boolean onSoftRight() {
        return onBack();
    }

    @Override
    public boolean onBack() {
        exit();
        return true;
    }

    // ---------- 业务逻辑 ----------

    private void handleConfirm() {
        String text = editInput != null ? editInput.getText().toString().trim() : "";
        if (required && text.length() == 0) {
            Toast.makeText(requireContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (confirmListener != null) {
            confirmListener.onConfirm(text);
        }
        exit();
    }

    /** 退出本页：优先弹出返回栈，否则关闭宿主 Activity */
    private void exit() {
        if (getActivity() instanceof io.github.cctyl.nokia.common.ui.page.NokiaPageHost) {
            ((io.github.cctyl.nokia.common.ui.page.NokiaPageHost) getActivity()).exitCurrent();
        } else if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
