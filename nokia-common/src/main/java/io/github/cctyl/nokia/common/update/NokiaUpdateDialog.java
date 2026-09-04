package io.github.cctyl.nokia.common.update;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.log.NokiaLog;
import io.github.cctyl.nokia.common.ui.dialog.NokiaConfirmDialog;

/**
 * 检查更新的可选 UI 层（复古诺基亚风格弹窗）。
 *
 * <p>纯逻辑检查请直接用 {@link NokiaUpdateChecker#check}；宿主不想自建界面时，
 * 一行调用即可完成「检查 → 弹窗 → 跳转」全流程：</p>
 *
 * <pre>{@code
 * NokiaUpdateDialog.checkAndShow(this,
 *         new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher"));
 * }</pre>
 *
 * <p>弹窗行为：</p>
 * <ul>
 *   <li>发现新版本：展示版本号与更新说明，左软键「更新」用系统浏览器打开
 *       APK 直链（无 APK 资产时打开 Release 页）；</li>
 *   <li>已是最新：简单提示；</li>
 *   <li>检查失败（GitHub 网络不畅）：提示用户，左软键「网盘下载」跳转
 *       {@link NokiaUpdateConfig#getFallbackUrl()}（默认百度网盘）。</li>
 * </ul>
 *
 * <p>注意：context 必须是 Activity（弹窗需要窗口 token）。</p>
 */
public final class NokiaUpdateDialog {

    private static final String TAG = "NokiaUpdateDialog";
    /** 更新说明在弹窗里的最大展示长度（超出截断加省略号） */
    private static final int MAX_CHANGELOG = 160;

    private NokiaUpdateDialog() {
    }

    /**
     * 一站式入口：异步检查，结束后按结果弹对应弹窗。
     *
     * @param context Activity 上下文（弹窗需要）
     * @param config  检查配置
     */
    public static void checkAndShow(@NonNull final Context context,
                                    @NonNull final NokiaUpdateConfig config) {
        NokiaUpdateChecker.check(context, config, new NokiaUpdateChecker.Callback() {
            @Override
            public void onResult(NokiaUpdateResult result) {
                showResult(context, result, config.getFallbackUrl());
            }
        });
    }

    /**
     * 按检查结果弹窗（宿主自检后也可直接调用）。
     *
     * @param context     Activity 上下文
     * @param result      检查结果
     * @param fallbackUrl 检查失败时的备用下载地址；传 null 用默认网盘地址
     */
    public static void showResult(@NonNull Context context,
                                  @Nullable NokiaUpdateResult result,
                                  @Nullable String fallbackUrl) {
        if (result == null) {
            return;
        }
        String fallback = (fallbackUrl == null || fallbackUrl.trim().isEmpty())
                ? NokiaUpdateConfig.DEFAULT_FALLBACK_URL : fallbackUrl;
        switch (result.status) {
            case UPDATE_AVAILABLE:
                showUpdateAvailable(context, result);
                break;
            case UP_TO_DATE:
                showUpToDate(context, result);
                break;
            case FAILED:
            default:
                showFailed(context, fallback);
                break;
        }
    }

    private static void showUpdateAvailable(Context context, NokiaUpdateResult result) {
        NokiaUpdateInfo info = result.info;
        StringBuilder msg = new StringBuilder();
        msg.append("当前版本：v").append(result.currentVersion)
                .append("\n最新版本：v").append(info.version);
        if (info.changelog != null) {
            msg.append("\n\n更新内容：\n").append(truncate(info.changelog, MAX_CHANGELOG));
        }

        new NokiaConfirmDialog(context, "发现新版本", msg.toString())
                .setPositiveButton("更新", () -> openUrl(context, info.resolveDownloadUrl()))
                .setNegativeButton("取消", null)
                .show();
    }

    private static void showUpToDate(Context context, NokiaUpdateResult result) {
        new NokiaConfirmDialog(context, "已是最新版本",
                "当前已是最新版本 v" + result.currentVersion)
                .setPositiveButton("确认", null)
                .setNegativeButton("关闭", null)
                .show();
    }

    private static void showFailed(Context context, String fallbackUrl) {
        new NokiaConfirmDialog(context, "检查更新失败",
                "无法连接 GitHub 获取版本信息。\n可前往网盘手动下载最新版本。")
                .setPositiveButton("网盘下载", () -> openUrl(context, fallbackUrl))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 用系统浏览器打开链接（带兜底日志，不抛异常） */
    private static void openUrl(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            NokiaLog.w(TAG, "openUrl aborted: empty url");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            NokiaLog.w(TAG, "no browser to open: " + url, e);
        } catch (Throwable t) {
            NokiaLog.w(TAG, "openUrl failed: " + url, t);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
