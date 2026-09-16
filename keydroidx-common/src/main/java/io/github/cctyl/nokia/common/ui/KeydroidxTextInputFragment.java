package io.github.cctyl.nokia.common.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.ui.dialog.KeydroidxOptionsDialog;
import io.github.cctyl.nokia.common.ui.page.KeydroidxPageFragment;

/**
 * 诺基亚风格「全屏文本编辑页」（生态通用组件，FEATURE_PHONE_UI_SPEC §19/§20）。
 *
 * <p>功能机（S40 / KaiOS）输入长文本的经典范式：进入全屏编辑页，输入区占据整个内容区，
 * 标题栏与软键条由宿主 {@link KeydroidxBaseActivity} 骨架固定在屏幕上下两端，
 * 软键条恒定可见（不会出现弹窗挤压软键条导致用户看不见「确定/取消」的问题）。</p>
 *
 * <h3>与旧版底部弹窗式输入框（已废弃）的区别</h3>
 * <ul>
 *   <li>底部弹窗（Dialog）→ 全屏页面（Fragment，压入返回栈）；</li>
 *   <li>输入区高度 33px → 占满整个内容区；</li>
 *   <li>软键条被压成 0×0 → 由宿主骨架绘制，恒定可见。</li>
 * </ul>
 *
 * <h3>用法（宿主 push 到 midPanel）</h3>
 * <pre>
 * KeydroidxTextInputFragment page = KeydroidxTextInputFragment.newInstance(
 *         "问题描述", comment, "描述问题与复现步骤", true, 0, 500);
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
public class KeydroidxTextInputFragment extends KeydroidxPageFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_TEXT = "text";
    private static final String ARG_HINT = "hint";
    private static final String ARG_MULTILINE = "multiline";
    private static final String ARG_MAX_CHARS = "maxChars";
    /** 按 UTF-8 字节数限制（用于服务端按字节计长的字段，如反馈 comment ≤500 字节）。 */
    private static final String ARG_MAX_BYTES = "maxBytes";

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
    private int maxBytes = 0;
    private boolean required = true;
    /** 上一次刷新软键栏时的「是否有内容」状态，用于只在状态翻转时刷新底栏 */
    private boolean lastHasText = false;

    private OnConfirmListener confirmListener;

    public KeydroidxTextInputFragment() {
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
    public static KeydroidxTextInputFragment newInstance(@NonNull String title, @Nullable String text,
                                                     @Nullable String hint, boolean multiline, int maxChars) {
        return newInstance(title, text, hint, multiline, maxChars, 0);
    }

    /**
     * 创建全屏编辑页（可同时按字符数与 UTF-8 字节数限制）。
     *
     * @param maxChars  最大字符数，0 表示不限制
     * @param maxBytes  最大 UTF-8 字节数，0 表示不限制。用于服务端按字节计长的字段
     *                  （如反馈 {@code comment} 协议上限 500 字节，中文 3 字节/字），
     *                  避免输入在客户端通过、到服务端被拒
     */
    public static KeydroidxTextInputFragment newInstance(@NonNull String title, @Nullable String text,
                                                     @Nullable String hint, boolean multiline,
                                                     int maxChars, int maxBytes) {
        KeydroidxTextInputFragment f = new KeydroidxTextInputFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_TEXT, text != null ? text : "");
        args.putString(ARG_HINT, hint != null ? hint : "");
        args.putBoolean(ARG_MULTILINE, multiline);
        args.putInt(ARG_MAX_CHARS, maxChars);
        args.putInt(ARG_MAX_BYTES, maxBytes);
        f.setArguments(args);
        return f;
    }

    public KeydroidxTextInputFragment setOnConfirmListener(@Nullable OnConfirmListener listener) {
        this.confirmListener = listener;
        return this;
    }

    /** 设置按 UTF-8 字节数限制（0 表示不限制）。 */
    public KeydroidxTextInputFragment setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
        return this;
    }

    /** 设置是否必填。必填时内容为空会提示而非直接返回。默认 true。 */
    public KeydroidxTextInputFragment setRequired(boolean required) {
        this.required = required;
        return this;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_keydroidx_text_input;
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
            maxBytes = args.getInt(ARG_MAX_BYTES, 0);
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

        editInput.setFilters(buildFilters(maxChars, maxBytes));

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
        if (maxBytes > 0) {
            // 字节计长优先：对齐服务端按 UTF-8 字节的硬限
            int bytes = editInput != null ? utf8Length(editInput.getText()) : 0;
            tvCounter.setText(bytes + "/" + maxBytes);
        } else if (maxChars > 0) {
            int len = editInput != null ? editInput.getText().length() : 0;
            tvCounter.setText(len + "/" + maxChars);
        } else {
            int len = editInput != null ? editInput.getText().length() : 0;
            tvCounter.setText(String.valueOf(len));
        }
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
        KeydroidxTheme.ThemeDef theme = KeydroidxUi.getTheme(requireContext());
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

    // ---------- 输入限制 ----------

    /**
     * 组装输入过滤器：字符数与 UTF-8 字节数两道闸，仅配置非 0 的生效。
     */
    private static InputFilter[] buildFilters(int maxChars, int maxBytes) {
        java.util.List<InputFilter> filters = new java.util.ArrayList<>(2);
        if (maxChars > 0) {
            filters.add(new InputFilter.LengthFilter(maxChars));
        }
        if (maxBytes > 0) {
            filters.add(new Utf8ByteLengthFilter(maxBytes));
        }
        return filters.isEmpty() ? new InputFilter[0]
                : filters.toArray(new InputFilter[0]);
    }

    /**
     * 按服务端 UTF-8 字节硬限裁剪输入，防止中文等 3 字节字符超过字段上限
     * （例如反馈 {@code comment} ≤500 字节，中文约 166 字即打满）。
     *
     * <p>语义对齐 {@link InputFilter.LengthFilter}：返回 {@code null} 表示保留原始插入文本，
     * 返回裁剪后的子序列替换插入文本，超限且无余量时返回空串拒绝。</p>
     */
    private static final class Utf8ByteLengthFilter implements InputFilter {
        private final int maxBytes;

        Utf8ByteLengthFilter(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            String destStr = dest.toString();
            String before = destStr.substring(0, dstart);
            String after = destStr.substring(dend);
            int baseBytes = utf8Length(before) + utf8Length(after);
            CharSequence insert = source.subSequence(start, end);
            if (baseBytes + utf8Length(insert) <= maxBytes) {
                return null; // 余量充足，放行
            }
            int allowed = maxBytes - baseBytes;
            if (allowed <= 0) {
                return ""; // 已满，拒绝插入
            }
            // 逐字符累加直到余量耗尽，裁剪到合法字符边界（不在代理对中间截断）
            StringBuilder sb = new StringBuilder();
            int used = 0;
            int i = start;
            while (i < end) {
                char c = source.charAt(i);
                int b;
                if (Character.isHighSurrogate(c) && i + 1 < end
                        && Character.isLowSurrogate(source.charAt(i + 1))) {
                    b = 4;
                    if (used + b > allowed) break;
                    sb.append(c).append(source.charAt(i + 1));
                    used += b;
                    i += 2;
                } else {
                    b = (c <= 0x7F) ? 1 : (c <= 0x7FF) ? 2 : 3;
                    if (used + b > allowed) break;
                    sb.append(c);
                    used += b;
                    i++;
                }
            }
            return sb;
        }
    }

    /** 计算 {@link CharSequence} 编码为 UTF-8 后的字节数（正确处理代理对）。 */
    private static int utf8Length(CharSequence s) {
        if (s == null) return 0;
        int n = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c <= 0x7F) {
                n += 1;
            } else if (c <= 0x7FF) {
                n += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < len
                    && Character.isLowSurrogate(s.charAt(i + 1))) {
                n += 4;
                i++;
            } else {
                n += 3;
            }
        }
        return n;
    }

    // ---------- KeydroidxPage 契约 ----------

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
        KeydroidxOptionsDialog dialog = new KeydroidxOptionsDialog(requireContext(), "选项");
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
        if (getActivity() instanceof io.github.cctyl.nokia.common.ui.page.KeydroidxPageHost) {
            ((io.github.cctyl.nokia.common.ui.page.KeydroidxPageHost) getActivity()).exitCurrent();
        } else if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
