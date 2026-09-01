package io.github.cctyl.nokia.common.ui.apppicker;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.cctyl.nokia.common.R;
import io.github.cctyl.nokia.common.log.NokiaLog;
import io.github.cctyl.nokia.common.ui.NokiaFontManager;
import io.github.cctyl.nokia.common.ui.page.NokiaListPageFragment;
import io.github.cctyl.nokia.common.util.NokiaDimens;

/**
 * 通用应用选择器 Fragment（诺基亚复古风格列表）。
 * 仅列举已安装的 Android 启动器应用，支持键盘方向键导航、确定键直接切换勾选/取消勾选，不隐藏已选应用。
 */
public abstract class NokiaAppPickerFragment extends NokiaListPageFragment {

    private static final String TAG = "NokiaAppPicker";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LinearLayout appListLayout;
    private TextView tvSelectedCount;
    private TextView shortcutTitle;

    private final List<NokiaAppItem> allApps = new ArrayList<>();
    private final Set<String> selectedPackages = new LinkedHashSet<>();

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_nokia_app_picker;
    }

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        appListLayout = view.findViewById(R.id.appListLayout);
        listScroll = view.findViewById(R.id.appScroll);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        shortcutTitle = view.findViewById(R.id.shortcutTitle);
        constrainScrollHeight(view, listScroll);

        Set<String> initial = getInitialSelectedPackages();
        if (initial != null) {
            selectedPackages.addAll(initial);
        }

        loadAppsAsync();
    }

    /**
     * 获取初始已选中的包名集合
     */
    @NonNull
    protected abstract Set<String> getInitialSelectedPackages();

    /**
     * 选中状态变更回调（即时持久化或通知）
     */
    protected abstract void onSelectionChanged(@NonNull Set<String> selectedPackages, @NonNull String pkg, boolean isSelected);

    private void loadAppsAsync() {
        EXECUTOR.execute(() -> {
            List<NokiaAppItem> list = loadInstalledApps();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(list);
                buildAppList();
            });
        });
    }

    private List<NokiaAppItem> loadInstalledApps() {
        List<NokiaAppItem> result = new ArrayList<>();
        if (getContext() == null) return result;
        PackageManager pm = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(main, 0);
        String selfPkg = getContext().getPackageName();
        Set<String> seenPkgs = new HashSet<>();

        for (ResolveInfo ri : resolveInfos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) continue;
            if (ai.packageName.equals(selfPkg)) continue;
            if (!seenPkgs.add(ai.packageName)) continue;

            CharSequence labelCs = ri.loadLabel(pm);
            String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : ai.name;
            Drawable icon = ri.loadIcon(pm);
            result.add(new NokiaAppItem(ai.packageName, label, icon));
        }

        // 按名称排序
        Collections.sort(result, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
        return result;
    }

    private void buildAppList() {
        if (appListLayout == null || getContext() == null) return;
        appListLayout.removeAllViews();

        if (allApps.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("未找到可管理的应用");
            empty.setTextColor(0xFFAAAAAA);
            NokiaFontManager.setTextSize(empty, 12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, NokiaDimens.dp(getResources(), 20), 0, 0);
            appListLayout.addView(empty);
            itemViews = new View[0];
            updateCountText();
            return;
        }

        itemViews = new View[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            NokiaAppItem app = allApps.get(i);
            String pkg = app.getPackageName();

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, NokiaDimens.dp(getResources(), 38)));
            row.setPadding(NokiaDimens.dp(getResources(), 8), NokiaDimens.dp(getResources(), 3),
                    NokiaDimens.dp(getResources(), 8), NokiaDimens.dp(getResources(), 3));
            row.setClickable(true);

            // 复选框 [√] 或 [ ]
            TextView tvCheck = new TextView(requireContext());
            tvCheck.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            NokiaFontManager.setTextSize(tvCheck, 12);
            applyCheckState(tvCheck, pkg);
            tvCheck.setTag("check_" + i);
            row.addView(tvCheck);

            // 应用图标
            ImageView iv = new ImageView(requireContext());
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    NokiaDimens.dp(getResources(), 24), NokiaDimens.dp(getResources(), 24)));
            if (app.getIcon() != null) {
                iv.setImageDrawable(app.getIcon());
            }
            row.addView(iv);

            // 间隔
            View space = new View(requireContext());
            space.setLayoutParams(new LinearLayout.LayoutParams(NokiaDimens.dp(getResources(), 8), 1));
            row.addView(space);

            // 应用名称
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(app.getAppName());
            tv.setTextColor(0xFFFFFFFF);
            NokiaFontManager.setTextSize(tv, 12);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tv);

            row.setTag(pkg);
            final int index = i;
            row.setOnClickListener(v -> {
                setFocusIndex(index);
                toggleSelection(index);
            });

            appListLayout.addView(row);
            itemViews[i] = row;
        }

        updateCountText();
        setFocusIndex(0);
    }

    private void applyCheckState(TextView check, String pkg) {
        if (check == null) return;
        boolean selected = selectedPackages.contains(pkg);
        check.setText(selected ? "[√] " : "[ ] ");
        check.setTextColor(selected ? 0xFF00FF66 : 0xFF888888);
    }

    private void toggleSelection(int index) {
        if (index < 0 || index >= allApps.size()) return;
        NokiaAppItem app = allApps.get(index);
        String pkg = app.getPackageName();

        boolean newState;
        if (selectedPackages.contains(pkg)) {
            selectedPackages.remove(pkg);
            newState = false;
        } else {
            selectedPackages.add(pkg);
            newState = true;
        }

        if (itemViews != null && index < itemViews.length && itemViews[index] != null) {
            View row = itemViews[index];
            TextView check = row.findViewWithTag("check_" + index);
            if (check != null) {
                applyCheckState(check, pkg);
            }
        }
        updateCountText();
        onSelectionChanged(selectedPackages, pkg, newState);
    }

    private void updateCountText() {
        if (tvSelectedCount != null) {
            tvSelectedCount.setText("已选 " + selectedPackages.size() + " / " + allApps.size() + " 项");
        }
    }

    @Override
    public boolean onSelect() {
        if (focusIndex >= 0 && focusIndex < allApps.size()) {
            toggleSelection(focusIndex);
        }
        return true;
    }

    @Override
    public boolean onSoftLeft() {
        return false;
    }

    @Override
    public boolean onSoftRight() {
        if (getActivity() != null) {
            getActivity().finish();
        }
        return true;
    }

    @Override
    public boolean onBack() {
        if (getActivity() != null) {
            getActivity().finish();
        }
        return true;
    }

    @Override
    public String getSoftLeftText() {
        return null;
    }

    @Override
    public String getSoftRightText() {
        return "返回";
    }
}
