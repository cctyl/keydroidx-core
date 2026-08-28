package io.github.cctyl.nokia.common.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog;
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
 *   <li>LSK：选项菜单（粘贴 / 复制全部 / 清空全部 / 保存并退出 / 退出不保存）；</li>
 *   <li>CSK（确认键）：确定，校验后回传结果并出栈（单行/多行一致）；</li>
 *   <li>RSK：有内容时为「清除」（退格删一个字符，对齐 J2ME TextBox 的 C 键语义）；
 *       内容为空时为「返回」（放弃修改并出栈）；</li>
 *   <li>BACK：放弃修改并出栈。</li>
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
    /** 上一次刷新软键栏时的「是否有内容」状态，用于只在状态翻转时刷新底栏 */
    private boolean lastHasText = false;

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
                // 内容「有/无」翻转时右键文案要在「清除 / 返回」之间切换，需通知宿主刷新底栏
                boolean hasText = hasText();
                if (hasText != lastHasText) {
                    lastHasText = hasText;
                    notifyHostRefresh();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        lastHasText = hasText();

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

    /** 输入框当前是否有内容（用于决定右键是「清除」还是「返回」）。 */
    private boolean hasText() {
        return editInput != null && editInput.getText().length() > 0;
    }

    /**
     * 退格删除光标前一个字符；存在选区时删除选区。
     * 对齐 J2ME {@code TextBox.deletePreviousChar()} 的 C 键语义。
     */
    private void deletePreviousChar() {
        if (editInput == null) return;
        Editable text = editInput.getText();
        int start = editInput.getSelectionStart();
        int end = editInput.getSelectionEnd();
        if (start != end) {
            text.delete(Math.min(start, end), Math.max(start, end));
            return;
        }
        if (start > 0) {
            text.delete(start - 1, start);
        }
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
        return "选项";
    }

    @Override
    public CharSequence getSoftCenterText() {
        return "确定";
    }

    @Override
    public CharSequence getSoftRightText() {
        // 有内容时右键是「清除」，空内容时才是「返回」（对齐 J2ME TextBox / ScreenSoftBar 的语义）
        return hasText() ? "清除" : "返回";
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
        // 确认键与左软键等价：确定的唯一入口，单行/多行行为一致
        handleConfirm();
        return true;
    }

    @Override
    public boolean onSoftLeft() {
        showOptionsMenu();
        return true;
    }

    @Override
    public boolean onSoftRight() {
        // 有内容：退格删一个字符（不退页面）；无内容：退出本页
        if (hasText()) {
            deletePreviousChar();
            return true;
        }
        return onBack();
    }

    @Override
    public boolean onBack() {
        exit();
        return true;
    }

    // ---------- 选项菜单（LSK） ----------

    private static final int OPT_PASTE = 0;
    private static final int OPT_COPY_ALL = 1;
    private static final int OPT_CLEAR_ALL = 2;
    private static final int OPT_SAVE_EXIT = 3;
    private static final int OPT_EXIT_NO_SAVE = 4;

    private void showOptionsMenu() {
        NokiaOptionsDialog dialog = new NokiaOptionsDialog(requireContext(), "选项");
        dialog.addItem(OPT_PASTE, "粘贴");
        dialog.addItem(OPT_COPY_ALL, "复制全部");
        dialog.addItem(OPT_CLEAR_ALL, "清空全部");
        dialog.addItem(OPT_SAVE_EXIT, "保存并退出");
        dialog.addItem(OPT_EXIT_NO_SAVE, "退出（不保存内容）");
        dialog.setOnOptionSelectedListener((index, item) -> onOptionSelected(item.getId()));
        dialog.show();
    }

    private void onOptionSelected(int id) {
        switch (id) {
            case OPT_PASTE:
                pasteFromClipboard();
                break;
            case OPT_COPY_ALL:
                copyAllToClipboard();
                break;
            case OPT_CLEAR_ALL:
                if (editInput != null) editInput.setText("");
                break;
            case OPT_SAVE_EXIT:
                handleConfirm();
                break;
            case OPT_EXIT_NO_SAVE:
                exit();
                break;
            default:
                break;
        }
    }

    private void pasteFromClipboard() {
        if (editInput == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence pasted = clip.getItemAt(0).coerceToText(requireContext());
        if (pasted == null || pasted.length() == 0) return;
        int start = Math.min(editInput.getSelectionStart(), editInput.getSelectionEnd());
        int end = Math.max(editInput.getSelectionStart(), editInput.getSelectionEnd());
        editInput.getText().replace(start, end, pasted);
    }

    private void copyAllToClipboard() {
        if (editInput == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        String text = editInput.getText().toString();
        cm.setPrimaryClip(ClipData.newPlainText(title, text));
        if (text.length() > 0) {
            Toast.makeText(requireContext(), "已复制全部内容", Toast.LENGTH_SHORT).show();
        }
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
