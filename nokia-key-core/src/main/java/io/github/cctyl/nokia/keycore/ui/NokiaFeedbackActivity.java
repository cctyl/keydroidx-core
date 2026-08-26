package io.github.cctyl.nokia.keycore.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Map;

import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.feedback.NokiaFeedback;
import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.feedback.NokiaFeedbackConfig;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.ui.dialog.NokiaOptionsDialog;
import io.github.cctyl.nokia.keycore.ui.NokiaTheme;

/**
 * 诺基亚风格通用反馈页（设置页式表单，遵循 FEATURE_PHONE_UI_SPEC）。
 *
 * <p>宿主 APP 只需两步：</p>
 * <ol>
 *   <li>启动时初始化：{@code NokiaFeedback.init(new NokiaFeedbackConfig(...))}；</li>
 *   <li>在任意入口（如设置页）跳转：{@code startActivity(new Intent(this, NokiaFeedbackActivity.class))}。</li>
 * </ol>
 *
 * <h3>按键映射（规范 §3/§4/§34）</h3>
 * <ul>
 *   <li>UP/DOWN：焦点逐行移动（不循环）；</li>
 *   <li>问题类型行：LEFT/RIGHT 快速切换值；CENTER 弹出选项菜单；</li>
 *   <li>联系方式 / 问题描述行：CENTER 进入全屏输入页；</li>
 *   <li>日志行：始终开启（仅展示状态），不可关闭；</li>
 *   <li>提交行：CENTER 提交；</li>
 *   <li>软键：LSK=提交，RSK=返回。</li>
 * </ul>
 */
public class NokiaFeedbackActivity extends NokiaBaseActivity {

    private static final String LOG_LIMIT_HINT = "单文件超8MB截断，总量上限约9MB";

    private static final String[] TYPE_NAMES = {"崩溃闪退", "功能异常", "体验建议", "其他"};
    private static final String[] TYPE_KEYS = {"crash", "bug", "suggest", "other"};

    /** 焦点行索引 */
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

    /** 表单状态 */
    private int selectedType = -1;
    private String contact = "";
    private String comment = "";

    private NokiaTheme.ThemeDef theme;
    private int focusRow = -1;
    private boolean submitting = false;

    @Override
    protected int getContentLayoutRes() {
        return R.layout.activity_nokia_feedback;
    }

    @Override
    protected void onInitViews() {
        theme = NokiaClient.get(this).getCurrentTheme();
        applyThemeColors();
        setPageTitle("意见反馈");
        setTitleIcon(NokiaIcons.ICON_EDIT);
        setSoftKeys("提交", "", "返回");

        rows[ROW_TYPE] = findViewById(R.id.rowType);
        rows[ROW_CONTACT] = findViewById(R.id.rowContact);
        rows[ROW_COMMENT] = findViewById(R.id.rowComment);
        rows[ROW_LOG] = findViewById(R.id.rowLog);
        rows[ROW_SUBMIT] = findViewById(R.id.rowSubmit);

        valType = findViewById(R.id.valType);
        valContact = findViewById(R.id.valContact);
        valComment = findViewById(R.id.valComment);
        tvLogInfo = findViewById(R.id.tvLogInfo);
        valSubmit = findViewById(R.id.valSubmit);

        refreshValues();
        refreshLogInfo();
        setFocusRow(ROW_TYPE); // 规范 §54：每屏定义 initialFocus
    }

    @Override
    protected void onResume() {
        super.onResume();
        theme = NokiaClient.get(this).getCurrentTheme();
        applyThemeColors();
        for (int i = 0; i < ROW_COUNT; i++) {
            applyRowBackground(i, i == focusRow);
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
        File logDir = NokiaFeedback.resolveLogDir(this);
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
                startTextInput("联系方式", contact, "QQ / 邮箱 / 手机号", 100, REQ_CONTACT);
                break;
            case ROW_COMMENT:
                startTextInput("问题描述", comment, "描述问题与复现步骤", 500, REQ_COMMENT);
                break;
            case ROW_LOG:
                // 日志必须附带，仅展示状态
                Toast.makeText(this, "日志将随反馈一并提交", Toast.LENGTH_SHORT).show();
                break;
            case ROW_SUBMIT:
                doSubmit();
                break;
        }
    }

    private void openTypeMenu() {
        NokiaOptionsDialog dialog = new NokiaOptionsDialog(this, "问题类型");
        for (int i = 0; i < TYPE_NAMES.length; i++) {
            dialog.addItem(i, (selectedType == i ? "● " : "○ ") + TYPE_NAMES[i]);
        }
        dialog.setOnOptionSelectedListener(new NokiaOptionsDialog.OnOptionSelectedListener() {
            @Override
            public void onOptionSelected(int index, NokiaOptionsDialog.OptionItem item) {
                selectedType = index;
                refreshValues();
            }
        });
        dialog.show();
    }

    private static final int REQ_CONTACT = 1001;
    private static final int REQ_COMMENT = 1002;

    private void startTextInput(String title, String current, String hint, int maxChars, int requestCode) {
        Intent it = new Intent(this, NokiaTextInputActivity.class)
                .putExtra(NokiaTextInputActivity.EXTRA_TITLE, title)
                .putExtra(NokiaTextInputActivity.EXTRA_HINT, hint)
                .putExtra(NokiaTextInputActivity.EXTRA_TEXT, current)
                .putExtra(NokiaTextInputActivity.EXTRA_MAX_CHARS, maxChars);
        startActivityForResult(it, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        String text = data.getStringExtra(NokiaTextInputActivity.RESULT_TEXT);
        if (text == null) return;
        if (requestCode == REQ_CONTACT) {
            contact = text;
            refreshValues();
        } else if (requestCode == REQ_COMMENT) {
            comment = text;
            refreshValues();
        }
    }

    // ---------- 提交 ----------

    private void doSubmit() {
        if (submitting) return;

        NokiaFeedbackConfig config = NokiaFeedback.getConfig();
        if (config == null || !config.isValid()) {
            Toast.makeText(this, "反馈功能未配置", Toast.LENGTH_LONG).show();
            return;
        }
        if (contact.length() == 0) {
            setFocusRow(ROW_CONTACT);
            activateRow(ROW_CONTACT); // 直接打开输入对话框引导填写
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

        NokiaFeedback.submit(this, contact, comment, extra, true,
                new NokiaFeedback.Callback() {
                    @Override
                    public void onResult(boolean success) {
                        submitting = false;
                        valSubmit.setText("提 交 反 馈");
                        Toast.makeText(NokiaFeedbackActivity.this,
                                success ? "感谢反馈！" : "提交失败，请稍后重试",
                                Toast.LENGTH_SHORT).show();
                        if (success) finish();
                    }
                });
    }

    // ---------- 焦点导航（规范 §6：逐行移动，不循环） ----------

    private void setFocusRow(int row) {
        if (row < 0 || row >= ROW_COUNT) return;
        if (focusRow >= 0 && focusRow < ROW_COUNT && rows[focusRow] != null) {
            applyRowBackground(focusRow, false);
        }
        focusRow = row;
        View v = rows[focusRow];
        if (v != null) {
            applyRowBackground(focusRow, true);
            smoothScrollToVisible(findViewById(R.id.feedbackScroll), v);
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
    }

    /** 按当前主题为页面各元素着色（与桌面主题保持一致） */
    private void applyThemeColors() {
        if (theme == null) return;
        View page = findViewById(R.id.feedbackScroll);
        if (page != null) {
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{theme.primaryColor, theme.darkColor});
            page.setBackground(bg);
        }
        int[] labelIds = {R.id.lblType, R.id.lblContact, R.id.lblComment, R.id.lblLog};
        for (int id : labelIds) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setTextColor(theme.textColor);
        }
        TextView logInfo = findViewById(R.id.tvLogInfo);
        if (logInfo != null) logInfo.setTextColor(theme.subTextColor & 0x00FFFFFF | 0xB0000000);
        TextView submit = findViewById(R.id.valSubmit);
        if (submit != null) submit.setTextColor(theme.focusColor == theme.accentColor
                ? brighten(theme.accentColor) : theme.focusColor);
        TextView note = findViewById(R.id.tvPrivacyNote);
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

    // ---------- 按键处理（规范 §3：方向键导航 / CENTER 激活） ----------

    @Override
    protected boolean onAction(int action) {
        if (submitting) {
            return true; // 提交中屏蔽全部按键
        }
        switch (action) {
            case NokiaKeyAction.UP:
                if (focusRow > 0) {
                    setFocusRow(focusRow - 1);
                    return true;
                }
                return true; // 首行不再上移（默认不循环）
            case NokiaKeyAction.DOWN:
                if (focusRow < ROW_COUNT - 1) {
                    setFocusRow(focusRow + 1);
                    return true;
                }
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
            case NokiaKeyAction.SELECT:
                if (focusRow >= 0) {
                    activateRow(focusRow);
                    return true;
                }
                break;
            case NokiaKeyAction.SOFT_LEFT:
                doSubmit();
                return true;
            case NokiaKeyAction.SOFT_RIGHT:
                finish();
                return true;
        }
        return super.onAction(action);
    }
}
