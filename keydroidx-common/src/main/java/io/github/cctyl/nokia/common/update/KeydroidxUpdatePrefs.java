package io.github.cctyl.nokia.common.update;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自动检查更新的持久化配置（SharedPreferences 封装，风格对齐 core 其它 {@code nokia_*} prefs）。
 *
 * <p>存储项：</p>
 * <ul>
 *   <li>{@code auto_check_enabled}：自动检查开关，默认开启；</li>
 *   <li>{@code ignored_version}：用户选择忽略的版本号，此后只有比它更新的版本才会再提醒；</li>
 *   <li>{@code last_check_day}：上次成功检查的日期（yyyyMMdd），实现「一天最多检查一次」；</li>
 *   <li>{@code pending_update}：每日检查发现、等待进入应用后弹窗提醒的新版本信息（JSON）。</li>
 * </ul>
 */
public final class KeydroidxUpdatePrefs {

    private static final String PREFS_NAME = "nokia_update_prefs";
    private static final String KEY_AUTO_CHECK_ENABLED = "auto_check_enabled";
    private static final String KEY_IGNORED_VERSION = "ignored_version";
    private static final String KEY_LAST_CHECK_DAY = "last_check_day";
    private static final String KEY_LAST_REMIND_DAY = "last_remind_day";
    private static final String KEY_PENDING_UPDATE = "pending_update";

    /** 更新说明落盘前的最大长度（弹窗展示时会再截断，这里防止 SP 无限膨胀） */
    private static final int MAX_STORED_CHANGELOG = 400;

    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyyMMdd", Locale.US);

    private KeydroidxUpdatePrefs() {
    }

    static SharedPreferences prefs(Context context) {
        // attachBaseContext 阶段 Application.getApplicationContext() 会返回 null，
        // 此时直接用传入 context（其 mBase 已就绪）；其余阶段照常用 applicationContext
        Context c = context.getApplicationContext();
        if (c == null) {
            c = context;
        }
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ───────────────────────── 自动检查开关 ─────────────────────────

    /** 自动检查更新是否开启（默认 true：未设置过时视为开启）。 */
    public static boolean isAutoCheckEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CHECK_ENABLED, true);
    }

    public static void setAutoCheckEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply();
    }

    // ───────────────────────── 忽略本版本 ─────────────────────────

    /** 用户忽略的版本号（归一化，无 v 前缀）；从未忽略返回 ""。 */
    public static String getIgnoredVersion(Context context) {
        return prefs(context).getString(KEY_IGNORED_VERSION, "");
    }

    /**
     * 忽略指定版本：直到远端出现比它更新的版本，才恢复更新通知。
     */
    public static void setIgnoredVersion(Context context, String version) {
        prefs(context).edit().putString(KEY_IGNORED_VERSION,
                version == null ? "" : version.trim()).apply();
    }

    public static void clearIgnoredVersion(Context context) {
        prefs(context).edit().remove(KEY_IGNORED_VERSION).apply();
    }

    // ───────────────────────── 按天节流 ─────────────────────────

    /** 今天（本地时区）是否已经成功检查过。 */
    public static boolean isCheckedToday(Context context) {
        String today = DAY_FORMAT.format(new Date());
        return today.equals(prefs(context).getString(KEY_LAST_CHECK_DAY, ""));
    }

    /** 记录「今天已检查成功」，供 {@link #isCheckedToday} 次日自动失效。 */
    public static void markCheckedToday(Context context) {
        prefs(context).edit()
                .putString(KEY_LAST_CHECK_DAY, DAY_FORMAT.format(new Date()))
                .apply();
    }

    // ───────────────────────── 弹窗按天提醒 ─────────────────────────

    /** 今天是否已经弹过更新提醒（无论用户当时选了更新/忽略还是直接关闭）。 */
    public static boolean wasRemindedToday(Context context) {
        String today = DAY_FORMAT.format(new Date());
        return today.equals(prefs(context).getString(KEY_LAST_REMIND_DAY, ""));
    }

    /** 记录「今天已弹过提醒」，次日自动失效。 */
    public static void markRemindedToday(Context context) {
        prefs(context).edit()
                .putString(KEY_LAST_REMIND_DAY, DAY_FORMAT.format(new Date()))
                .apply();
    }

    // ───────────────────────── 待提醒的新版本（弹窗用） ─────────────────────────

    /** 读取待提醒的新版本信息；无记录或解析失败返回 null。 */
    public static KeydroidxUpdateInfo getPendingUpdate(Context context) {
        String json = prefs(context).getString(KEY_PENDING_UPDATE, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(json);
            return new KeydroidxUpdateInfo(
                    o.optString("version", ""),
                    o.optString("tag", ""),
                    o.optString("name", null),
                    o.optString("html_url", null),
                    o.optString("download_url", null),
                    o.optString("asset_name", null),
                    o.optLong("asset_size", -1),
                    o.optString("changelog", null),
                    null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 记录待提醒的新版本（弹窗展示用；更新说明截断到 400 字符）。 */
    public static void setPendingUpdate(Context context, KeydroidxUpdateInfo info) {
        if (info == null || info.version == null || info.version.isEmpty()) {
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("version", info.version);
            o.put("tag", info.tagName == null ? "" : info.tagName);
            if (info.releaseName != null) {
                o.put("name", info.releaseName);
            }
            if (info.htmlUrl != null) {
                o.put("html_url", info.htmlUrl);
            }
            if (info.downloadUrl != null) {
                o.put("download_url", info.downloadUrl);
            }
            if (info.assetName != null) {
                o.put("asset_name", info.assetName);
            }
            o.put("asset_size", info.assetSize);
            if (info.changelog != null) {
                String cl = info.changelog.length() > MAX_STORED_CHANGELOG
                        ? info.changelog.substring(0, MAX_STORED_CHANGELOG) : info.changelog;
                o.put("changelog", cl);
            }
            prefs(context).edit().putString(KEY_PENDING_UPDATE, o.toString()).apply();
        } catch (Throwable ignored) {
            // JSON 组装失败时放弃缓存，下次每日检查会重新写入
        }
    }

    public static void clearPendingUpdate(Context context) {
        prefs(context).edit().remove(KEY_PENDING_UPDATE).apply();
    }
}
