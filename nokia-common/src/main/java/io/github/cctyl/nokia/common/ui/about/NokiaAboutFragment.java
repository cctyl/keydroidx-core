package io.github.cctyl.nokia.common.ui.about;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.log.NokiaLog;
import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.NokiaFontManager;
import io.github.cctyl.nokia.common.ui.NokiaIcons;
import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog;
import io.github.cctyl.nokia.common.ui.page.NokiaScrollPageFragment;
import io.github.cctyl.nokia.common.update.NokiaUpdateConfig;
import io.github.cctyl.nokia.common.update.NokiaUpdateDialog;

/**
 * KeydroidX 生态标准复古关于页面。
 * 数据驱动、零物理按键吞键、支持点阵字体自适应与统一详细日志开关。
 */
public class NokiaAboutFragment extends NokiaScrollPageFragment {

    protected static final String ARG_CONFIG = "arg_about_config";

    private NokiaAboutConfig config;
    private final List<InteractiveItem> interactiveItems = new ArrayList<>();
    private int focusedIndex = 0;

    private static class InteractiveItem {
        enum Type { URL, LOG_TOGGLE, CUSTOM, CHECK_UPDATE }
        final Type type;
        final String title;
        final String subtitle;
        final String url;
        final View cardView;
        TextView subtitleView;

        InteractiveItem(Type type, String title, String subtitle, String url, View cardView, TextView subtitleView) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.cardView = cardView;
            this.subtitleView = subtitleView;
        }
    }

    public static NokiaAboutFragment newInstance(@Nullable NokiaAboutConfig config) {
        NokiaAboutFragment fragment = new NokiaAboutFragment();
        Bundle args = new Bundle();
        if (config != null) {
            args.putSerializable(ARG_CONFIG, config);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_nokia_about;
    }

    @Override
    public CharSequence getPageTitle() {
        return "关于";
    }

    @Override
    public CharSequence getSoftLeftText() {
        if (!interactiveItems.isEmpty() && focusedIndex >= 0 && focusedIndex < interactiveItems.size()) {
            InteractiveItem item = interactiveItems.get(focusedIndex);
            if (item.type == InteractiveItem.Type.LOG_TOGGLE) {
                return "切换";
            } else if (item.type == InteractiveItem.Type.CHECK_UPDATE) {
                return "检查";
            }
        }
        return "选项";
    }

    @Nullable
    @Override
    public CharSequence getSoftCenterText() {
        if (!interactiveItems.isEmpty() && focusedIndex >= 0 && focusedIndex < interactiveItems.size()) {
            InteractiveItem item = interactiveItems.get(focusedIndex);
            if (item.type == InteractiveItem.Type.LOG_TOGGLE) {
                return "切换";
            } else if (item.type == InteractiveItem.Type.URL) {
                return "打开";
            } else if (item.type == InteractiveItem.Type.CHECK_UPDATE) {
                return "检查";
            }
        }
        return "选择";
    }

    @Override
    public CharSequence getSoftRightText() {
        return "返回";
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().containsKey(ARG_CONFIG)) {
            config = (NokiaAboutConfig) getArguments().getSerializable(ARG_CONFIG);
        }
        if (config == null && getContext() != null) {
            config = NokiaAboutConfig.createDefault(getContext());
        }
    }

    @Override
    protected void onScrollPageCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        if (getContext() != null) {
            initViews(root);
        }
    }

    private void initViews(@NonNull View root) {
        if (config == null && getContext() != null) {
            config = NokiaAboutConfig.createDefault(getContext());
        }
        if (config == null) return;

        // 1. 顶部 Header
        ImageView ivIcon = root.findViewById(R.id.iv_about_icon);
        TextView tvName = root.findViewById(R.id.tv_about_name);
        TextView tvVersion = root.findViewById(R.id.tv_about_version);

        if (config.getAppIconRes() != 0) {
            ivIcon.setImageResource(config.getAppIconRes());
            ivIcon.setVisibility(View.VISIBLE);
        } else {
            ivIcon.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(config.getAppName())) {
            tvName.setText(config.getAppName());
        }
        if (!TextUtils.isEmpty(config.getVersionName())) {
            tvVersion.setText(config.getVersionName());
        }

        // 2. 描述
        TextView tvDesc = root.findViewById(R.id.tv_about_desc);
        if (!TextUtils.isEmpty(config.getDescription())) {
            tvDesc.setText(config.getDescription());
            tvDesc.setVisibility(View.VISIBLE);
        } else {
            tvDesc.setVisibility(View.GONE);
        }

        // 3. 动态可交互卡片（链接 / 日志开关）
        LinearLayout llLinks = root.findViewById(R.id.ll_about_links);
        llLinks.removeAllViews();
        interactiveItems.clear();

        // 3.1 检查更新（复用 repoUrl，置于链接区首位作为主操作）
        if (config.isShowUpdateCheck() && !TextUtils.isEmpty(config.getRepoUrl())) {
            addCheckUpdateCard(llLinks);
        }

        // 3.2 开源地址
        if (!TextUtils.isEmpty(config.getRepoUrl())) {
            addUrlCard(llLinks, "开源地址 (GitHub)", config.getRepoUrl(), "#90CAF9");
        }

        // 3.3 演示视频
        if (!TextUtils.isEmpty(config.getVideoUrl())) {
            addUrlCard(llLinks, "演示视频 (Bilibili)", config.getVideoUrl(), "#FF80AB");
        }

        // 3.4 额外自定义链接
        for (NokiaAboutConfig.LinkItem link : config.getExtraLinks()) {
            addUrlCard(llLinks, link.getTitle(), link.getUrl(), "#80DEEA");
        }

        // 3.5 详细日志开关
        if (config.isShowDetailedLogToggle()) {
            addLogToggleCard(llLinks);
        }

        // 4. 作者信息
        LinearLayout llAuthor = root.findViewById(R.id.ll_about_author);
        TextView iconAuthor = root.findViewById(R.id.icon_about_author);
        TextView tvAuthor = root.findViewById(R.id.tv_about_author);
        if (!TextUtils.isEmpty(config.getAuthor())) {
            NokiaIcons.setIcon(iconAuthor, NokiaIcons.PERSON);
            tvAuthor.setText("作者: " + config.getAuthor());
            llAuthor.setVisibility(View.VISIBLE);
        } else {
            llAuthor.setVisibility(View.GONE);
        }

        // 5. 致谢清单
        LinearLayout llThanks = root.findViewById(R.id.ll_about_thanks);
        TextView tvThanksContent = root.findViewById(R.id.tv_about_thanks_content);
        if (!TextUtils.isEmpty(config.getAcknowledgements())) {
            tvThanksContent.setText(config.getAcknowledgements());
            llThanks.setVisibility(View.VISIBLE);
        } else {
            llThanks.setVisibility(View.GONE);
        }

        // 6. 声明说明
        TextView tvStatement = root.findViewById(R.id.tv_about_statement);
        if (!TextUtils.isEmpty(config.getExtraStatement())) {
            tvStatement.setText(config.getExtraStatement());
            tvStatement.setVisibility(View.VISIBLE);
        } else {
            tvStatement.setVisibility(View.GONE);
        }

        // 应用点阵字体与缩放
        NokiaFontManager.applyToViewTree(root);

        updateFocusHighlight();
    }

    private void addUrlCard(LinearLayout container, String title, String url, String linkColorHex) {
        if (getContext() == null) return;
        Context ctx = getContext();

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        card.setClickable(true);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        NokiaFontManager.setTextSize(tvTitle, TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.getPaint().setFakeBoldText(true);

        TextView tvUrl = new TextView(ctx);
        tvUrl.setText(url);
        tvUrl.setTextColor(Color.parseColor(linkColorHex));
        NokiaFontManager.setTextSize(tvUrl, TypedValue.COMPLEX_UNIT_SP, 10);
        tvUrl.setPadding(0, dp(2), 0, 0);

        card.addView(tvTitle);
        card.addView(tvUrl);

        final int idx = interactiveItems.size();
        card.setOnClickListener(v -> {
            focusedIndex = idx;
            updateFocusHighlight();
            showUrlOptions(interactiveItems.get(idx));
        });

        interactiveItems.add(new InteractiveItem(InteractiveItem.Type.URL, title, url, url, card, tvUrl));
        container.addView(card);
    }

    private void addCheckUpdateCard(LinearLayout container) {
        if (getContext() == null) return;
        Context ctx = getContext();

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        card.setClickable(true);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("检查更新");
        tvTitle.setTextColor(Color.WHITE);
        NokiaFontManager.setTextSize(tvTitle, TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.getPaint().setFakeBoldText(true);

        TextView tvSub = new TextView(ctx);
        tvSub.setText("查看 GitHub 最新版本");
        tvSub.setTextColor(Color.parseColor("#A5D6A7"));
        NokiaFontManager.setTextSize(tvSub, TypedValue.COMPLEX_UNIT_SP, 10);
        tvSub.setPadding(0, dp(2), 0, 0);

        card.addView(tvTitle);
        card.addView(tvSub);

        final int idx = interactiveItems.size();
        card.setOnClickListener(v -> {
            focusedIndex = idx;
            updateFocusHighlight();
            checkUpdate();
        });

        interactiveItems.add(new InteractiveItem(InteractiveItem.Type.CHECK_UPDATE,
                "检查更新", null, null, card, tvSub));
        container.addView(card);
    }

    private void checkUpdate() {
        if (getContext() == null || config == null || TextUtils.isEmpty(config.getRepoUrl())) return;
        if (getActivity() == null) return;
        Toast.makeText(getContext(), "正在检查更新…", Toast.LENGTH_SHORT).show();
        NokiaUpdateConfig updateConfig = new NokiaUpdateConfig(config.getRepoUrl());
        // 宿主可传入剥干净的逻辑版本号，覆盖默认读 PackageInfo.versionName 的行为
        // （用于版本号带渠道后缀的场景，如 1.3.1-open → 1.3.1）
        if (!TextUtils.isEmpty(config.getUpdateCurrentVersion())) {
            updateConfig.setCurrentVersion(config.getUpdateCurrentVersion());
        }
        NokiaUpdateDialog.checkAndShow(getActivity(), updateConfig);
    }

    private void addLogToggleCard(LinearLayout container) {
        if (getContext() == null) return;
        Context ctx = getContext();

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        card.setClickable(true);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("详细日志输出 (Debug Log)");
        tvTitle.setTextColor(Color.WHITE);
        NokiaFontManager.setTextSize(tvTitle, TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.getPaint().setFakeBoldText(true);

        TextView tvSub = new TextView(ctx);
        boolean enabled = NokiaLog.isDetailedLogEnabled(ctx);
        tvSub.setText(enabled ? "已开启 (详细记录所有调试日志)" : "已关闭 (仅记录错误日志)");
        tvSub.setTextColor(enabled ? Color.parseColor("#81C784") : Color.parseColor("#B0BEC5"));
        NokiaFontManager.setTextSize(tvSub, TypedValue.COMPLEX_UNIT_SP, 10);
        tvSub.setPadding(0, dp(2), 0, 0);

        card.addView(tvTitle);
        card.addView(tvSub);

        final int idx = interactiveItems.size();
        card.setOnClickListener(v -> {
            focusedIndex = idx;
            updateFocusHighlight();
            toggleDetailedLog();
        });

        interactiveItems.add(new InteractiveItem(InteractiveItem.Type.LOG_TOGGLE, "详细日志", null, null, card, tvSub));
        container.addView(card);
    }

    private void updateFocusHighlight() {
        if (getContext() == null) return;
        Context ctx = getContext();

        for (int i = 0; i < interactiveItems.size(); i++) {
            InteractiveItem item = interactiveItems.get(i);
            if (i == focusedIndex) {
                item.cardView.setBackground(NokiaTheme.createSelectionDrawable(ctx, dp(4)));
            } else {
                item.cardView.setBackground(null);
            }
        }
        notifyHostRefresh();
    }

    private void toggleDetailedLog() {
        if (getContext() == null) return;
        Context ctx = getContext();
        boolean newState = !NokiaLog.isDetailedLogEnabled(ctx);
        NokiaLog.setDetailedLogEnabled(ctx, newState);
        for (InteractiveItem item : interactiveItems) {
            if (item.type == InteractiveItem.Type.LOG_TOGGLE && item.subtitleView != null) {
                item.subtitleView.setText(newState ? "已开启 (详细记录所有调试日志)" : "已关闭 (仅记录错误日志)");
                item.subtitleView.setTextColor(newState ? Color.parseColor("#81C784") : Color.parseColor("#B0BEC5"));
                break;
            }
        }
        Toast.makeText(ctx, newState ? "已开启详细日志" : "已关闭详细日志", Toast.LENGTH_SHORT).show();
    }

    private void showUrlOptions(InteractiveItem item) {
        if (getContext() == null || TextUtils.isEmpty(item.url)) return;

        NokiaOptionsDialog dialog = new NokiaOptionsDialog(getContext(), item.title)
                .addItem(1, "在浏览器中打开")
                .addItem(2, "复制链接到剪贴板")
                .addItem(3, "返回")
                .setOnOptionSelectedListener((index, opt) -> {
                    if (opt.getId() == 1) {
                        openBrowser(item.url);
                    } else if (opt.getId() == 2) {
                        copyToClipboard(item.url);
                    }
                });
        dialog.show();
    }

    private void openBrowser(String url) {
        if (getContext() == null || TextUtils.isEmpty(url)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开链接: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        if (getContext() == null || TextUtils.isEmpty(text)) return;
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("KeydroidX Link", text));
            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onDirection(int direction) {
        if (direction == NokiaKeyAction.UP) {
            if (focusedIndex > 0) {
                focusedIndex--;
                updateFocusHighlight();
                ensureFocusedVisible();
                return true;
            }
        } else if (direction == NokiaKeyAction.DOWN) {
            if (focusedIndex < interactiveItems.size() - 1) {
                focusedIndex++;
                updateFocusHighlight();
                ensureFocusedVisible();
                return true;
            }
        }
        return super.onDirection(direction);
    }

    @Override
    public boolean onSelect() {
        if (!interactiveItems.isEmpty() && focusedIndex >= 0 && focusedIndex < interactiveItems.size()) {
            InteractiveItem item = interactiveItems.get(focusedIndex);
            if (item.type == InteractiveItem.Type.LOG_TOGGLE) {
                toggleDetailedLog();
            } else if (item.type == InteractiveItem.Type.URL) {
                showUrlOptions(item);
            } else if (item.type == InteractiveItem.Type.CHECK_UPDATE) {
                checkUpdate();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onSoftLeft() {
        return onSelect();
    }

    private void ensureFocusedVisible() {
        if (getView() == null || interactiveItems.isEmpty() || focusedIndex < 0 || focusedIndex >= interactiveItems.size()) {
            return;
        }
        ScrollView sv = getView().findViewById(R.id.about_scroll);
        if (sv != null) {
            View card = interactiveItems.get(focusedIndex).cardView;
            smoothScrollToVisible(sv, card);
        }
    }

    private int dp(int val) {
        if (getContext() == null) return val;
        return (int) (val * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
