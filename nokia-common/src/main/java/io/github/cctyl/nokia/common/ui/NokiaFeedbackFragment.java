package io.github.cctyl.nokia.common.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Map;

import io.github.cctyl.nokia.common.feedback.NokiaFeedback;
import io.github.cctyl.nokia.common.feedback.NokiaFeedbackConfig;
import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog;
import io.github.cctyl.nokia.common.ui.page.NokiaPageFragment;
import io.github.cctyl.nokia.common.R;

/**
 * 诺基亚风格通用反馈页（Fragment 形态，生态唯一实现）。
 *
 * <p>属于 {@code nokia-common}，零业务依赖：主题取自全局 {@link ThemeProvider}，
 * 按键由宿主 Activity（实现 {@link io.github.cctyl.nokia.common.model.KeyResolver}）解析，
 * 文本输入复用 {@link NokiaTextInputFragment}（全屏编辑页），日志上传复用 {@link NokiaFeedback}。</p>
 *
 * <h3>两端的接入方式</h3>
 * <ul>
 *   <li>桌面 Launcher：直接 {@code pushFragment(new NokiaFeedbackFragment())} 压入桌面 Fragment 栈；</li>
 *   <li>独立 App（经 {@code nokia-key-core}）：由 {@code NokiaFeedbackActivity} 超薄壳托管本 Fragment。</li>
 * </ul>
 *
 * <h3>按键映射</h3>
 * <ul>
 *   <li>UP/DOWN：焦点逐行移动（不循环）；</li>
 *   <li>问题类型行：LEFT/RIGHT 快速切换值；CENTER 弹出选项菜单；</li>
 *   <li>联系方式 / 问题描述行：CENTER 进入全屏编辑页；</li>
 *   <li>日志行：始终附带，仅展示状态；</li>
 *   <li>提交行：CENTER 提交；</li>
 *   <li>软键：LSK=提交，RSK=返回。</li>
 * </ul>
 */
public class NokiaFeedbackFragment extends NokiaPageFragment {

    private static final String LOG_LIMIT_HINT = "单文件超8MB截断，总量上限约9MB";

    private static final String[] TYPE_NAMES = {"崩溃闪退", "功能异常", "体验建议", "其他"};
    private static final String[] TYPE_KEYS = {"crash", "bug", "suggest", "other"};

    private static final int ROW_TYPE = 0;
    private static final int ROW_CONTACT = 1;
    private static final int ROW_COMMENT = 2;
    private static final int ROW_LOG = 3;
    private static final int ROW_SUBMIT = 4;
    private static final int ROW_COUNT = 5;

    private View[] rows = new View[ROW_COUNT];
    private TextView valType;
    private TextView valContact;
    private TextView valComment;
    private TextView tvLogInfo;
    private TextView valSubmit;

    private int selectedType = -1;
    private String contact = "";
    private String comment = "";

    private NokiaTheme.ThemeDef theme;
    private int focusRow = -1;
    private boolean submitting = false;

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_nokia_feedback;
    }

    @Override
    public CharSequence getPageTitle() {
        return "意见反馈";
    }

    @Override
    public CharSequence getSoftLeftText() {
        return "提交";
    }

    @Override
    public CharSequence getSoftCenterText() {
        if (focusRow == ROW_SUBMIT) {
            return "提交";
        }
        return "选择";
    }

    @Override
    public CharSequence getSoftRightText() {
        return "返回";
    }

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        theme = NokiaUi.getTheme(requireContext());

        rows[ROW_TYPE] = view.findViewById(R.id.rowType);
        rows[ROW_CONTACT] = view.findViewById(R.id.rowContact);
        rows[ROW_COMMENT] = view.findViewById(R.id.rowComment);
        rows[ROW_LOG] = view.findViewById(R.id.rowLog);
        rows[ROW_SUBMIT] = view.findViewById(R.id.rowSubmit);

        valType = view.findViewById(R.id.valType);
        valContact = view.findViewById(R.id.valContact);
        valComment = view.findViewById(R.id.valComment);
        tvLogInfo = view.findViewById(R.id.tvLogInfo);
        valSubmit = view.findViewById(R.id.valSubmit);

        applyThemeColors();
        refreshValues();
        refreshLogInfo();
        setFocusRow(ROW_TYPE);
        if (rows[ROW_TYPE] != null) {
            rows[ROW_TYPE].post(() -> {
                if (rows[ROW_TYPE] != null) {
                    rows[ROW_TYPE].requestFocus();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        theme = NokiaUi.getTheme(requireContext());
        applyThemeColors();
        for (int i = 0; i < ROW_COUNT; i++) {
            applyRowBackground(i, i == focusRow);
        }
        if (focusRow >= 0 && focusRow < ROW_COUNT && rows[focusRow] != null) {
            rows[focusRow].requestFocus();
        }
        refreshValues();
        refreshLogInfo();
    }

    // ---------- 值显示 ----------

    private void refreshValues() {
        if (selectedType >= 0) {
            valType.setText(TYPE_NAMES[selectedType] + " >");
            valType.setTextColor(theme.textColor);
        } else {
            valType.setText("请选择 >");
            valType.setTextColor(theme.subTextColor);
        }
        setValText(valContact, contact, "请输入 >");
        setValText(valComment, comment, "请输入 >");
    }

    private void setValText(TextView tv, String value, String hint) {
        if (value.length() == 0) {
            tv.setText(hint);
            tv.setTextColor(theme.subTextColor);
        } else {
            tv.setText(value + " >");
            tv.setTextColor(theme.textColor);
        }
    }

    private void refreshLogInfo() {
        File logDir = NokiaFeedback.resolveLogDir(requireContext());
        if (!logDir.isDirectory()) {
            tvLogInfo.setText("未发现日志目录");
            return;
        }
        File[] files = logDir.listFiles();
        int count = files == null ? 0 : files.length;
        long total = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) total += f.length();
            }
        }
        if (total > 9L * 1024 * 1024) {
            tvLogInfo.setText(String.format("%d 个文件 / %s（过大仅保留最新）· %s",
                    count, formatSize(total), LOG_LIMIT_HINT));
        } else {
            tvLogInfo.setText(String.format("%d 个文件 / %s · %s",
                    count, formatSize(total), LOG_LIMIT_HINT));
        }
    }

    private void cycleType(int delta) {
        selectedType = (selectedType + delta + TYPE_NAMES.length) % TYPE_NAMES.length;
        refreshValues();
    }

    // ---------- 行激活（CENTER）----------

    private void activateRow(int row) {
        switch (row) {
            case ROW_TYPE:
                openTypeMenu();
                break;
            case ROW_CONTACT:
                startTextInput("联系方式", contact, "QQ / 邮箱 / 手机号", false, 100);
                break;
            case ROW_COMMENT:
                startTextInput("问题描述", comment, "描述问题与复现步骤", true, 500);
                break;
            case ROW_LOG:
                Toast.makeText(requireContext(), "日志将随反馈一并提交", Toast.LENGTH_SHORT).show();
                break;
            case ROW_SUBMIT:
                doSubmit();
                break;
        }
    }

    private void openTypeMenu() {
        NokiaOptionsDialog dialog = new NokiaOptionsDialog(requireContext(), "问题类型");
        for (int i = 0; i < TYPE_NAMES.length; i++) {
            dialog.addItem(i, (selectedType == i ? "● " : "○ ") + TYPE_NAMES[i]);
        }
        dialog.setOnOptionSelectedListener((index, item) -> {
            selectedType = index;
            refreshValues();
        });
        dialog.show();
    }

    /**
     * 进入全屏编辑页（功能机 S40 范式：全屏输入，软键条恒定可见）。
     *
     * <p>编辑页压入返回栈，确定后回调写回字段并出栈恢复本页焦点。</p>
     */
    private void startTextInput(String title, String current, String hint, boolean multiline, int maxChars) {
        NokiaTextInputFragment page = NokiaTextInputFragment.newInstance(
                title, current, hint, multiline, maxChars);
        page.setOnConfirmListener(text -> {
            if (title.equals("联系方式")) {
                contact = text;
            } else {
                comment = text;
            }
            refreshValues();
        });

        // 压入宿主 Fragment 返回栈（容器为骨架的 midPanel）
        int containerId = getHostContainerId();
        getParentFragmentManager().beginTransaction()
                .replace(containerId, page)
                .addToBackStack(null)
                .commit();
    }

    /**
     * 获取宿主内容容器 ID。优先使用骨架 midPanel，宿主未提供时退回本 Fragment 所在容器。
     */
    private int getHostContainerId() {
        if (getActivity() instanceof io.github.cctyl.nokia.common.ui.NokiaBaseActivity) {
            return io.github.cctyl.nokia.common.R.id.midPanel;
        }
        View own = getView();
        if (own != null && own.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) own.getParent();
            int id = parent.getId();
            if (id != View.NO_ID) {
                return id;
            }
        }
        return io.github.cctyl.nokia.common.R.id.midPanel;
    }

    // ---------- 提交 ----------

    private void doSubmit() {
        if (submitting) return;

        NokiaFeedbackConfig config = NokiaFeedback.getConfig();
        if (config == null || !config.isValid()) {
            Toast.makeText(requireContext(), "反馈功能未配置", Toast.LENGTH_LONG).show();
            return;
        }
        if (contact.length() == 0) {
            setFocusRow(ROW_CONTACT);
            activateRow(ROW_CONTACT);
            return;
        }
        if (selectedType < 0) {
            setFocusRow(ROW_TYPE);
            openTypeMenu();
            return;
        }
        if (comment.length() == 0) {
            setFocusRow(ROW_COMMENT);
            activateRow(ROW_COMMENT);
            return;
        }

        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        extra.put("feedback_type", TYPE_KEYS[selectedType]);
        extra.put("feedback_type_name", TYPE_NAMES[selectedType]);

        submitting = true;
        valSubmit.setText("提交中...");

        NokiaFeedback.submit(requireContext(), contact, comment, extra, true,
                new NokiaFeedback.Callback() {
                    @Override
                    public void onResult(boolean success) {
                        submitting = false;
                        valSubmit.setText("提 交 反 馈");
                        Toast.makeText(requireContext(),
                                success ? "感谢反馈！" : "提交失败，请稍后重试",
                                Toast.LENGTH_SHORT).show();
                        if (success && isAdded()) {
                            requireActivity().finish();
                        }
                    }
                });
    }

    // ---------- 焦点导航 ----------

    private void setFocusRow(int row) {
        if (row < 0 || row >= ROW_COUNT) return;
        if (focusRow >= 0 && focusRow < ROW_COUNT && rows[focusRow] != null) {
            applyRowBackground(focusRow, false);
        }
        focusRow = row;
        notifyHostRefresh();
        View v = rows[focusRow];
        if (v != null) {
            applyRowBackground(focusRow, true);
            v.requestFocus();
            smoothScrollToVisible((ScrollView) getView().findViewById(R.id.feedbackScroll), v);
        }
    }

    private void applyRowBackground(int row, boolean focused) {
        View v = rows[row];
        if (v == null || theme == null) return;
        float density = getResources().getDisplayMetrics().density;
        if (focused) {
            v.setBackground(theme.createSelectedRowDrawable(4 * density));
        } else {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(theme.cardBgColor);
            gd.setCornerRadius(3 * density);
            gd.setStroke((int) (1 * density), theme.accentColor);
            v.setBackground(gd);
        }
        // 提交行文字需随焦点反色：非焦点用 focusColor 醒目提示可点击，
        // 焦点时行背景即为 focusColor，必须改用 textColor 否则文字会融进背景。
        if (row == ROW_SUBMIT) {
            applySubmitTextColor(focused);
        }
    }

    /** 提交行文字配色：与行背景保持足够反差，避免选中后文字不可见。 */
    private void applySubmitTextColor(boolean focused) {
        if (valSubmit == null || theme == null) return;
        valSubmit.setTextColor(focused ? theme.textColor : resolveSubmitAccentColor());
    }

    /** 非焦点态的强调色。与 accentColor 同色时提亮，避免与卡片底色过于接近。 */
    private int resolveSubmitAccentColor() {
        return theme.focusColor == theme.accentColor
                ? brighten(theme.accentColor) : theme.focusColor;
    }

    private void applyThemeColors() {
        if (theme == null || getView() == null) return;
        View page = getView().findViewById(R.id.feedbackScroll);
        if (page != null) {
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{theme.primaryColor, theme.darkColor});
            page.setBackground(bg);
        }
        int[] labelIds = {R.id.lblType, R.id.lblContact, R.id.lblComment, R.id.lblLog};
        for (int id : labelIds) {
            TextView tv = getView().findViewById(id);
            if (tv != null) tv.setTextColor(theme.textColor);
        }
        TextView logInfo = getView().findViewById(R.id.tvLogInfo);
        if (logInfo != null) logInfo.setTextColor(theme.subTextColor & 0x00FFFFFF | 0xB0000000);
        // 提交行文字由 applyRowBackground 按焦点态统一处理，此处不重复设置
        applySubmitTextColor(focusRow == ROW_SUBMIT);
        TextView note = getView().findViewById(R.id.tvPrivacyNote);
        if (note != null) note.setTextColor(theme.subTextColor & 0x00FFFFFF | 0x80000000);
    }

    private static int brighten(int color) {
        int r = Math.min(255, (color >> 16 & 0xFF) + 70);
        int g = Math.min(255, (color >> 8 & 0xFF) + 70);
        int b = Math.min(255, (color & 0xFF) + 70);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1fMB", bytes / 1048576f);
        if (bytes >= 1024) return String.format("%.1fKB", bytes / 1024f);
        return bytes + "B";
    }

    // ---------- 按键分发（NokiaPage 契约） ----------

    @Override
    public boolean onDirection(int direction) {
        if (submitting) return true;
        switch (direction) {
            case NokiaKeyAction.UP:
                if (focusRow > 0) setFocusRow(focusRow - 1);
                return true; // 首行不再上移（不循环）
            case NokiaKeyAction.DOWN:
                if (focusRow < ROW_COUNT - 1) setFocusRow(focusRow + 1);
                return true; // 末行不再下移
            case NokiaKeyAction.LEFT:
                if (focusRow == ROW_TYPE) {
                    cycleType(-1);
                    return true;
                }
                break;
            case NokiaKeyAction.RIGHT:
                if (focusRow == ROW_TYPE) {
                    cycleType(1);
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onSelect() {
        if (submitting) return true;
        if (focusRow >= 0) {
            activateRow(focusRow);
            return true;
        }
        return false;
    }

    @Override
    public boolean onSoftLeft() {
        doSubmit();
        return true;
    }

    @Override
    public boolean onSoftRight() {
        return onBack();
    }
}
