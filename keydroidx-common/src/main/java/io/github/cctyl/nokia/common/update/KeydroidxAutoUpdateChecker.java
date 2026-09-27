package io.github.cctyl.nokia.common.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.log.KeydroidxLog;

/**
 * 每日一次的自动检查更新（纯逻辑 + 进入应用后的复古弹窗提醒，无通知、无 Activity 依赖的后台部分）。
 *
 * <h3>分两步接入（宿主 Application 与主界面各一行）</h3>
 * <pre>{@code
 * // 1) Application.attachBaseContext 主进程块：每日后台检查（建议延迟数百毫秒以上）
 * KeydroidxAutoUpdateChecker.checkOncePerDay(this,
 *         new KeydroidxUpdateConfig("https://github.com/cctyl/keydroidx-launcher"), 8000);
 *
 * // 2) 主界面 Activity.onCreate（向导/首帧完成之后）：有待提醒版本则弹出确认弹窗
 * KeydroidxAutoUpdateChecker.showPendingUpdateDialog(this, updateConfig);
 * }</pre>
 *
 * <p>行为：</p>
 * <ul>
 *   <li>用户关闭了「自动检查更新」（见 {@link KeydroidxUpdatePrefs}）则完全静默；</li>
 *   <li>今天已成功检查过（本地时区按天记）则跳过；检查失败不记当天，下次启动再试；</li>
 *   <li>发现新版本且未被用户忽略 → 存「待提醒」记录，等宿主主界面调用
 *       {@link #showPendingUpdateDialog} 弹窗；</li>
 *   <li>弹窗每天最多一次：弹出当天（含用户 BACK 关闭）不再重弹，次日恢复；</li>
 *   <li>弹窗：LSK「更新」= 浏览器打开 APK 直链（无直链时打开 Release 页）；
 *       RSK「忽略此版本」= 该版本永不再提醒，直到出现更新的版本；BACK = 仅关闭，
 *       当天不再弹、次日再提醒。</li>
 * </ul>
 *
 * <p>弹窗复用 {@link KeydroidxConfirmDialog}（功能机规范：LSK/CENTER=确认、RSK=取消、
 * 按键解析与点阵字体由组件自理），宿主需声明 {@code android.permission.INTERNET}。</p>
 */
public final class KeydroidxAutoUpdateChecker {

    private static final String TAG = "KeydroidxAutoUpdate";

    /** 更新说明在弹窗里的最大展示长度（弹窗内容区可滚动，这里只是安全上限） */
    private static final int MAX_CHANGELOG_SHOWN = 400;

    private KeydroidxAutoUpdateChecker() {
    }

    // ───────────────────────── 第一步：每日后台检查 ─────────────────────────

    /**
     * 每日一次自动检查（立即执行）。
     * 内部依次做：开关检查 → 当天是否已查过 → 异步请求 → 新版本写「待提醒」记录。
     */
    public static void checkOncePerDay(Context context, KeydroidxUpdateConfig config) {
        checkOncePerDay(context, config, 0L);
    }

    /**
     * 每日一次自动检查（延迟 {@code delayMs} 毫秒执行，建议宿主传入数百毫秒以上，
     * 避开 Application 冷启动关键路径；节流判断在延迟执行时才做）。
     */
    public static void checkOncePerDay(Context context, final KeydroidxUpdateConfig config, long delayMs) {
        // 注意：宿主可能在 Application.attachBaseContext 里调用，此时
        // getApplicationContext() 尚未回填会返回 null，须回退使用传入的 context 本身
        Context resolved = null;
        if (context != null) {
            resolved = context.getApplicationContext();
            if (resolved == null) {
                resolved = context;
            }
        }
        final Context app = resolved;
        if (app == null) {
            KeydroidxLog.w(TAG, "checkOncePerDay aborted: null context");
            return;
        }
        if (!KeydroidxUpdatePrefs.isAutoCheckEnabled(app)) {
            KeydroidxLog.d(TAG, "auto check disabled by user, skip");
            return;
        }
        if (config == null || !config.isValid()) {
            KeydroidxLog.w(TAG, "auto check aborted: invalid config (repoUrl unreadable)");
            return;
        }
        Runnable run = new Runnable() {
            @Override
            public void run() {
                doDailyCheck(app, config);
            }
        };
        if (delayMs > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(run, delayMs);
        } else {
            run.run();
        }
    }

    /** 延迟后的真实入口：当天节流判断 + 异步检查。 */
    private static void doDailyCheck(Context app, KeydroidxUpdateConfig config) {
        if (!KeydroidxUpdatePrefs.isAutoCheckEnabled(app)) {
            KeydroidxLog.d(TAG, "auto check disabled by user, skip");
            return;
        }
        if (KeydroidxUpdatePrefs.isCheckedToday(app)) {
            KeydroidxLog.d(TAG, "already checked today, skip");
            return;
        }
        KeydroidxUpdateChecker.check(app, config, new KeydroidxUpdateChecker.Callback() {
            @Override
            public void onResult(KeydroidxUpdateResult result) {
                if (result == null) {
                    return;
                }
                if (result.status == KeydroidxUpdateResult.Status.UPDATE_AVAILABLE
                        && result.info != null) {
                    // 成功拿到远端版本 → 记今天已查
                    KeydroidxUpdatePrefs.markCheckedToday(app);
                    stashPending(app, result.info);
                } else if (result.status == KeydroidxUpdateResult.Status.UP_TO_DATE) {
                    KeydroidxUpdatePrefs.markCheckedToday(app);
                    KeydroidxLog.d(TAG, "auto check: up to date");
                } else {
                    // 失败不记当天：下次进程启动再重试
                    KeydroidxLog.w(TAG, "auto check failed: " + result.error);
                }
            }
        });
    }

    /** 新版本未被用户忽略时写入「待提醒」记录（弹窗数据源）。 */
    private static void stashPending(Context app, KeydroidxUpdateInfo info) {
        String ignored = KeydroidxUpdatePrefs.getIgnoredVersion(app);
        if (!ignored.isEmpty()
                && KeydroidxUpdateChecker.compareVersion(info.version, ignored) <= 0) {
            KeydroidxLog.d(TAG, "version " + info.version
                    + " <= ignored " + ignored + ", skip reminder");
            return;
        }
        KeydroidxUpdatePrefs.setPendingUpdate(app, info);
        KeydroidxLog.i(TAG, "update pending reminder saved: v" + info.version);
    }

    // ───────────────────────── 第二步：进入应用后弹窗 ─────────────────────────

    /**
     * 若存在「待提醒」的新版本且确实比宿主当前版本新，弹出复古确认弹窗。
     *
     * <p>建议在主界面 Activity 首帧就绪后（如 onCreate 末尾 postDelayed 约 1s）调用，
     * 避免与启动期的权限引导等弹窗抢焦点。</p>
     *
     * @param activity 宿主主界面（弹窗需要窗口 token）
     * @param config   检查配置（用于解析宿主当前版本号；传 null 时从 PackageInfo 读取）
     * @return 是否弹出了弹窗
     */
    public static boolean showPendingUpdateDialog(Activity activity,
                                                  @Nullable KeydroidxUpdateConfig config) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        Context app = activity.getApplicationContext();
        KeydroidxUpdateInfo info = KeydroidxUpdatePrefs.getPendingUpdate(app);
        if (info == null || info.version == null || info.version.isEmpty()) {
            return false;
        }
        String current = config != null
                ? config.resolveCurrentVersion(activity)
                : new KeydroidxUpdateConfig("a/b").resolveCurrentVersion(activity);
        if (KeydroidxUpdateChecker.compareVersion(info.version, current) <= 0) {
            // 宿主已升级到该版本或更高：缓存过期，清掉即可
            KeydroidxUpdatePrefs.clearPendingUpdate(app);
            KeydroidxLog.d(TAG, "pending update v" + info.version
                    + " stale (current " + current + "), cleared");
            return false;
        }
        if (KeydroidxUpdatePrefs.wasRemindedToday(app)) {
            // 今天已弹过（含用户 BACK 关闭的情况）：同一天内不再打扰，明天再说
            KeydroidxLog.d(TAG, "already reminded today, skip dialog");
            return false;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("当前版本：v").append(current)
                .append("\n最新版本：v").append(info.version);
        String changelog = sanitizeChangelog(info.changelog);
        if (changelog != null) {
            msg.append("\n\n更新内容：\n").append(changelog);
        }

        String downloadUrl = info.resolveDownloadUrl();
        KeydroidxUpdateReminderDialog dialog = new KeydroidxUpdateReminderDialog(
                activity, "发现新版本 v" + info.version, msg.toString())
                // LSK / CENTER：前往下载（浏览器打开直链或 Release 页）
                .setPositiveButton("更新", () -> {
                    KeydroidxUpdatePrefs.clearPendingUpdate(app);
                    openDownloadUrl(activity, downloadUrl);
                })
                // RSK：忽略该版本，直到出现更新的版本才恢复提醒
                .setNegativeButton("忽略此版本", () -> {
                    KeydroidxUpdatePrefs.setIgnoredVersion(app, info.version);
                    KeydroidxUpdatePrefs.clearPendingUpdate(app);
                    KeydroidxLog.i(TAG, "version ignored via dialog: " + info.version);
                });
        dialog.show();
        // 弹出即记当天：无论用户选更新/忽略还是 BACK 关闭，今天都不再弹
        KeydroidxUpdatePrefs.markRemindedToday(app);
        KeydroidxLog.i(TAG, "update dialog shown: v" + info.version);
        return true;
    }

    /**
     * 清洗 GitHub Release 更新说明用于弹窗展示：
     * 去 markdown 标题井号/加粗星号、去空行，超行数或超长截断加省略号，
     * 保证小屏 + 大字号下确认弹窗的底部软键栏不被挤出屏幕。
     */
    private static String sanitizeChangelog(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] lines = raw.split("\n");
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            l = l.replaceAll("^#+\\s*", "").replace("**", "");
            if (l.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(l);
            used++;
            if (sb.length() >= MAX_CHANGELOG_SHOWN) {
                break;
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        String s = sb.toString();
        if (s.length() > MAX_CHANGELOG_SHOWN) {
            s = s.substring(0, MAX_CHANGELOG_SHOWN);
        }
        // 内容被截断（行数或长度超限）时补省略号
        boolean truncated = used < countNonEmptyLines(lines)
                || s.length() < raw.replace("\r", "").replace("\n", "").trim().length();
        return truncated ? s + "…" : s;
    }

    private static int countNonEmptyLines(String[] lines) {
        int n = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 用系统浏览器打开下载/兜底链接（不抛异常）。 */
    private static void openDownloadUrl(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            url = KeydroidxUpdateConfig.DEFAULT_FALLBACK_URL;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            KeydroidxLog.w(TAG, "no browser to open: " + url, e);
        } catch (Throwable t) {
            KeydroidxLog.w(TAG, "open download url failed: " + url, t);
        }
    }
}
