package io.github.cctyl.nokia.common.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.github.cctyl.nokia.common.log.NokiaLog;

/**
 * 检查更新门面（纯逻辑、无 UI，风格对齐 {@code NokiaFeedback}）。
 *
 * <p>通过 GitHub Releases API 查询远端最新版本号，与宿主当前版本号对比；
 * GitHub 网络不畅导致检查失败时，宿主可用
 * {@link NokiaUpdateConfig#getFallbackUrl()}（默认百度网盘）引导用户手动下载。</p>
 *
 * <h3>接入示例（无 UI）</h3>
 * <pre>{@code
 * NokiaUpdateChecker.check(this,
 *         new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher"),
 *         result -> {
 *             if (result.status == NokiaUpdateResult.Status.UPDATE_AVAILABLE) {
 *                 // result.info.version / resolveDownloadUrl() / changelog ...
 *             }
 *         });
 * }</pre>
 *
 * <p>想要内置复古弹窗交互，直接用 {@link NokiaUpdateDialog#checkAndShow(Context, NokiaUpdateConfig)}。</p>
 *
 * <p>宿主需声明 {@code android.permission.INTERNET}。</p>
 */
public final class NokiaUpdateChecker {

    private static final String TAG = "NokiaUpdateChecker";
    private static final int MAX_BODY = 256 * 1024;

    private static final java.util.concurrent.ExecutorService sExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    // 懒加载：避免 JVM 单元测试环境（无 Android Looper）类初始化失败
    private static volatile Handler sMainHandler;

    private static Handler mainHandler() {
        if (sMainHandler == null) {
            synchronized (NokiaUpdateChecker.class) {
                if (sMainHandler == null) {
                    sMainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return sMainHandler;
    }

    private NokiaUpdateChecker() {
    }

    /** 异步检查结果回调（保证在主线程派发） */
    public interface Callback {
        void onResult(NokiaUpdateResult result);
    }

    /**
     * 异步检查更新。
     *
     * <p>内部在独立单线程池执行 HTTP 请求，结果回调切回主线程；
     * 任何异常（无网、超时、解析失败）都会归一为
     * {@link NokiaUpdateResult.Status#FAILED}，绝不抛出。</p>
     *
     * @param context 任意 Context（用于读取宿主 versionName），可为 null
     *                （此时 currentVersion 取配置值或 "0"）
     * @param config  检查配置（必须能解析出 owner/repo，否则直接回调 FAILED）
     * @param callback 主线程结果回调，可为 null
     */
    public static void check(Context context, NokiaUpdateConfig config, Callback callback) {
        final String currentVersion = config != null
                ? config.resolveCurrentVersion(context) : "0";
        if (config == null || !config.isValid()) {
            NokiaLog.w(TAG, "check aborted: invalid config (repoUrl unreadable)");
            if (callback != null) {
                callback.onResult(NokiaUpdateResult.failed("配置无效：无法从仓库地址解析出 owner/repo", currentVersion));
            }
            return;
        }
        sExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final NokiaUpdateResult result = doCheck(context != null
                        ? context.getApplicationContext() : null, config, currentVersion);
                if (callback != null) {
                    mainHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(result);
                        }
                    });
                }
            }
        });
    }

    /** 后台线程执行的真实检查逻辑 */
    private static NokiaUpdateResult doCheck(Context appContext,
                                             NokiaUpdateConfig config, String currentVersion) {
        try {
            String repoPath = config.resolveRepoPath();
            String body;
            if (config.isIncludePreRelease()) {
                // releases 列表按创建时间倒序，取第一个（含 pre-release）
                body = httpGet(NokiaUpdateConfig.GITHUB_API_BASE + repoPath + "/releases?per_page=5",
                        config.getTimeoutMs());
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject rel = arr.getJSONObject(i);
                    if (rel.optBoolean("draft", false)) {
                        continue;
                    }
                    NokiaUpdateInfo info = parseRelease(rel, config);
                    if (info == null) {
                        continue;
                    }
                    return buildResult(info, currentVersion);
                }
                return NokiaUpdateResult.failed("仓库暂无可用 Release", currentVersion);
            } else {
                body = httpGet(NokiaUpdateConfig.GITHUB_API_BASE + repoPath + "/releases/latest",
                        config.getTimeoutMs());
                JSONObject rel = new JSONObject(body);
                NokiaUpdateInfo info = parseRelease(rel, config);
                if (info == null) {
                    return NokiaUpdateResult.failed("仓库暂无可用 Release", currentVersion);
                }
                return buildResult(info, currentVersion);
            }
        } catch (Throwable t) {
            // 网络 / 解析异常统一视为检查失败，走备用下载引导
            NokiaLog.w(TAG, "check failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage(), t);
            return NokiaUpdateResult.failed("GitHub 访问失败：" + briefReason(t), currentVersion);
        }
    }

    private static NokiaUpdateResult buildResult(NokiaUpdateInfo info, String currentVersion) {
        if (info.isNewerThan(currentVersion)) {
            return NokiaUpdateResult.available(info, currentVersion);
        }
        return NokiaUpdateResult.upToDate(info, currentVersion);
    }

    /**
     * 从单个 Release JSON 解析版本信息。
     *
     * @return 解析失败（非 release / 无 tag）返回 null
     */
    static NokiaUpdateInfo parseRelease(JSONObject rel, NokiaUpdateConfig config) {
        String tag = rel.optString("tag_name", "").trim();
        if (tag.isEmpty()) {
            return null;
        }
        String version = NokiaUpdateConfig.normalizeVersion(tag);

        // 从 assets 里挑 APK 直链（忽略大小写 contains 匹配关键字）
        String downloadUrl = null;
        String assetName = null;
        long assetSize = -1;
        JSONArray assets = rel.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "").trim();
                if (matchesApkAsset(name, config != null ? config.getApkAssetKeyword() : null)) {
                    downloadUrl = asset.optString("browser_download_url", null);
                    assetName = name;
                    assetSize = asset.optLong("size", -1);
                    break;
                }
            }
        }

        String changelog = rel.optString("body", "").trim();
        if (changelog.isEmpty()) {
            changelog = null;
        }
        String htmlUrl = rel.optString("html_url", "");
        if (htmlUrl.isEmpty()) {
            htmlUrl = null;
        }
        return new NokiaUpdateInfo(version, tag,
                emptyToNull(rel.optString("name", "")),
                htmlUrl, downloadUrl, assetName, assetSize,
                changelog, emptyToNull(rel.optString("published_at", "")));
    }

    private static boolean matchesApkAsset(String assetName, String keyword) {
        if (assetName == null || assetName.isEmpty()) {
            return false;
        }
        String lower = assetName.toLowerCase();
        if (keyword != null && !keyword.trim().isEmpty()) {
            return lower.contains(keyword.trim().toLowerCase());
        }
        return lower.endsWith(".apk");
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s;
    }

    private static String briefReason(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    // ---------- HTTP ----------

    private static String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Connection", "close");
            // GitHub API 要求 UA，缺失会被 403
            conn.setRequestProperty("User-Agent", "KeydroidX-UpdateChecker");
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            int code = conn.getResponseCode();
            if (code == 404) {
                throw new IllegalStateException("仓库或 Release 不存在(404)");
            }
            if (code == 403) {
                throw new IllegalStateException("GitHub 拒绝访问(403，可能限流)");
            }
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code);
            }

            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BODY) {
                    throw new IllegalStateException("response too large");
                }
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- 版本号比较 ----------

    /**
     * 比较两个语义化版本号（容忍 v 前缀、点/横杠/下划线分隔、数字与字母混写）。
     *
     * <p>规则：数字段按数值比较；字母段（如 beta/rc）按字典序比较；
     * 相同前缀下带修饰段的版本视为更小（1.2.3-beta &lt; 1.2.3）。</p>
     *
     * @return 负数表示 a &lt; b，0 表示相等，正数表示 a &gt; b
     */
    public static int compareVersion(String a, String b) {
        String[] sa = splitVersion(a);
        String[] sb = splitVersion(b);
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            // 缺段补齐：对面是数字段补 "0"（1.2 == 1.2.0）；对面是修饰段补 ""（修饰段更小）
            String pa = i < sa.length ? sa[i] : (isNumeric(sb[i]) ? "0" : "");
            String pb = i < sb.length ? sb[i] : (isNumeric(sa[i]) ? "0" : "");
            int cmp = compareSegment(pa, pb);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /** 版本号切分：按 .-_+ 及数字/字母边界切，如 "1.2.3-beta2" → [1,2,3,beta,2] */
    private static String[] splitVersion(String v) {
        if (v == null) {
            return new String[0];
        }
        String s = v.trim();
        // 剥掉 v/V 前缀（仅当后面紧跟数字），否则会切出独立的 "v" 段污染比较
        if (s.length() > 1 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')
                && Character.isDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        java.util.List<String> segs = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Boolean lastWasDigit = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isSep = c == '.' || c == '-' || c == '_' || c == '+';
            boolean isDigit = Character.isDigit(c);
            boolean isLetter = Character.isLetter(c);
            if (isSep) {
                flushSegment(segs, cur);
                lastWasDigit = null;
                continue;
            }
            if (!Character.isLetterOrDigit(c)) {
                continue; // 忽略其它杂字符
            }
            boolean boundary = lastWasDigit != null && lastWasDigit != isDigit;
            if (boundary) {
                flushSegment(segs, cur);
            }
            cur.append(c);
            lastWasDigit = isDigit;
        }
        flushSegment(segs, cur);
        return segs.toArray(new String[0]);
    }

    private static void flushSegment(java.util.List<String> segs, StringBuilder cur) {
        if (cur.length() > 0) {
            segs.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static int compareSegment(String pa, String pb) {
        boolean aNum = isNumeric(pa);
        boolean bNum = isNumeric(pb);
        if (aNum && bNum) {
            // 去 0 后按长度比，避免超长数字溢出
            String a = stripLeadingZeros(pa);
            String b = stripLeadingZeros(pb);
            if (a.length() != b.length()) {
                return a.length() > b.length() ? 1 : -1;
            }
            int c = a.compareTo(b);
            return Integer.signum(c);
        }
        if (aNum != bNum) {
            // 数字段大于修饰段（正式版 1.2.3 > 预发布 1.2.3-beta）
            return aNum ? 1 : -1;
        }
        // 都非数字：缺段侧（""，正式版结尾）大于修饰段，其余按字典序
        if (pa.isEmpty() && pb.isEmpty()) {
            return 0;
        }
        if (pa.isEmpty()) {
            return 1;
        }
        if (pb.isEmpty()) {
            return -1;
        }
        int c = pa.compareToIgnoreCase(pb);
        return Integer.signum(c);
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String stripLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }
}
