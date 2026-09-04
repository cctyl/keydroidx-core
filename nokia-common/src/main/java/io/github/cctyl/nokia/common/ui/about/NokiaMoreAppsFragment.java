package io.github.cctyl.nokia.common.ui.about;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.ecosystem.KeydroidXApps;
import io.github.cctyl.nokia.common.ui.NokiaFontManager;
import io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog;
import io.github.cctyl.nokia.common.ui.page.NokiaListPageFragment;
import io.github.cctyl.nokia.common.ui.page.NokiaPageHost;

/**
 * 「更多应用」列表页：展示 common 内置的 KeydroidX 生态姊妹应用清单，
 * 每项含名称、简介、仓库地址，点击可打开浏览器或复制链接；末尾追加统一网盘地址卡片。
 * <p>
 * 继承 {@link NokiaListPageFragment} 复用循环焦点导航 + 高亮 + 滚动跟随。
 */
public class NokiaMoreAppsFragment extends NokiaListPageFragment {

    private static final String ARG_APPS = "arg_more_apps";

    private final List<KeydroidXApps.App> apps = new ArrayList<>();
    /** 每个列表项对应的标题与链接（应用项 + 末尾网盘项）。 */
    private String[] titles;
    private String[] urls;

    public static NokiaMoreAppsFragment newInstance(@Nullable List<KeydroidXApps.App> apps) {
        NokiaMoreAppsFragment f = new NokiaMoreAppsFragment();
        Bundle args = new Bundle();
        if (apps != null && !apps.isEmpty()) {
            // ArrayList 实现 Serializable，可放入 Bundle
            args.putSerializable(ARG_APPS, (Serializable) new ArrayList<>(apps));
        }
        f.setArguments(args);
        return f;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_nokia_more_apps;
    }

    @Override
    public CharSequence getPageTitle() {
        return "更多应用";
    }

    @Override
    public CharSequence getSoftLeftText() {
        return "打开";
    }

    @Nullable
    @Override
    public CharSequence getSoftCenterText() {
        return "选择";
    }

    @Override
    public CharSequence getSoftRightText() {
        return "返回";
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            Object obj = args.getSerializable(ARG_APPS);
            if (obj instanceof List) {
                for (Object o : (List<?>) obj) {
                    if (o instanceof KeydroidXApps.App) {
                        apps.add((KeydroidXApps.App) o);
                    }
                }
            }
        }
        // 未传入则展示全部（含自己，作为兜底）
        if (apps.isEmpty()) {
            apps.addAll(KeydroidXApps.all());
        }
    }

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        listScroll = view.findViewById(R.id.more_apps_scroll);
        LinearLayout ll = view.findViewById(R.id.more_apps_list);
        buildList(ll);
        setFocusIndex(0);
    }

    private void buildList(@NonNull LinearLayout container) {
        if (getContext() == null) return;
        container.removeAllViews();

        int n = apps.size();
        // 末尾追加网盘卡片
        int total = n + 1;
        itemViews = new View[total];
        titles = new String[total];
        urls = new String[total];

        for (int i = 0; i < n; i++) {
            KeydroidXApps.App app = apps.get(i);
            View card = addCard(container, app.getName(), app.getDesc(),
                    app.getRepoUrl(), "#90CAF9");
            final int idx = i;
            card.setTag(idx);
            itemViews[i] = card;
            titles[i] = app.getName();
            urls[i] = app.getRepoUrl();
        }
        // 末尾：统一网盘地址
        View panCard = addCard(container, "网盘地址 (" + KeydroidXApps.PAN_LABEL + ")",
                "提取码 " + KeydroidXApps.PAN_CODE, KeydroidXApps.PAN_URL, "#FFD54F");
        final int panIdx = n;
        panCard.setTag(panIdx);
        itemViews[n] = panCard;
        titles[n] = "网盘地址";
        urls[n] = KeydroidXApps.PAN_URL;
    }

    private View addCard(LinearLayout container, String title, String subtitle,
                         String url, String urlColorHex) {
        Context ctx = requireContext();

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

        TextView tvSub = new TextView(ctx);
        tvSub.setTextColor(Color.parseColor("#B0BEC5"));
        NokiaFontManager.setTextSize(tvSub, TypedValue.COMPLEX_UNIT_SP, 10);
        tvSub.setPadding(0, dp(2), 0, 0);

        TextView tvUrl = new TextView(ctx);
        tvUrl.setText(url);
        tvUrl.setTextColor(Color.parseColor(urlColorHex));
        NokiaFontManager.setTextSize(tvUrl, TypedValue.COMPLEX_UNIT_SP, 10);
        tvUrl.setPadding(0, dp(2), 0, 0);

        card.addView(tvTitle);
        if (!TextUtils.isEmpty(subtitle)) {
            tvSub.setText(subtitle);
            card.addView(tvSub);
        }
        card.addView(tvUrl);

        card.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (tag instanceof Integer) {
                setFocusIndex((Integer) tag);
            }
            openCurrent();
        });

        container.addView(card);
        return card;
    }

    private void openCurrent() {
        if (urls == null || focusIndex < 0 || focusIndex >= urls.length) return;
        String url = urls[focusIndex];
        String title = titles[focusIndex];
        if (TextUtils.isEmpty(url)) return;
        showUrlOptions(title, url);
    }

    @Override
    public boolean onSelect() {
        openCurrent();
        return true;
    }

    @Override
    public boolean onSoftLeft() {
        return onSelect();
    }

    @Override
    public boolean onSoftRight() {
        if (getActivity() instanceof NokiaPageHost) {
            ((NokiaPageHost) getActivity()).exitCurrent();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        return onSoftRight();
    }

    private void showUrlOptions(String title, String url) {
        if (getContext() == null || TextUtils.isEmpty(url)) return;
        new NokiaOptionsDialog(getContext(), title)
                .addItem(1, "在浏览器中打开")
                .addItem(2, "复制链接到剪贴板")
                .addItem(3, "返回")
                .setOnOptionSelectedListener((index, opt) -> {
                    if (opt.getId() == 1) {
                        openBrowser(url);
                    } else if (opt.getId() == 2) {
                        copyToClipboard(url);
                    }
                })
                .show();
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

    private int dp(int val) {
        if (getContext() == null) return val;
        return (int) (val * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
