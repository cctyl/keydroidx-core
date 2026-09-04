package io.github.cctyl.nokia.common.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * 检查更新的配置对象（链式装配，风格对齐 {@code NokiaAboutConfig}）。
 *
 * <p>唯一必填项是 GitHub 仓库地址；其余全部有合理默认值。</p>
 *
 * <h3>接入示例</h3>
 * <pre>{@code
 * NokiaUpdateConfig config = new NokiaUpdateConfig("https://github.com/cctyl/keydroidx-launcher")
 *         .setFallbackUrl(NokiaUpdateConfig.DEFAULT_FALLBACK_URL);
 * }</pre>
 */
public final class NokiaUpdateConfig {

    /**
     * 默认备用下载地址（百度网盘）。GitHub 网络不畅、检查失败时引导用户前往。
     */
    public static final String DEFAULT_FALLBACK_URL =
            "https://pan.baidu.com/s/1fA-sloEx9ERN-mK8wVXKzQ?pwd=fe66";

    /** GitHub API 基地址 */
    static final String GITHUB_API_BASE = "https://api.github.com/repos/";

    /** 用户填写的原始仓库地址 */
    private final String repoUrl;
    /** 检查失败时的备用下载地址（默认 {@link #DEFAULT_FALLBACK_URL}） */
    private String fallbackUrl = DEFAULT_FALLBACK_URL;
    /** 当前版本号；为 null 时自动从宿主 PackageInfo 读取 versionName */
    private String currentVersion;
    /** 是否把 pre-release 也纳入检查（默认 false，只看正式 Release） */
    private boolean includePreRelease = false;
    /**
     * APK 资产匹配关键字（对资产文件名做 contains 忽略大小写匹配）。
     * 为 null 时默认匹配任意 ".apk" 文件。
     */
    private String apkAssetKeyword;
    /** 连接/读取超时毫秒数 */
    private int timeoutMs = 10_000;

    public NokiaUpdateConfig(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    /**
     * 解析出 "owner/repo" 片段，用于拼接 GitHub API 地址。
     *
     * <p>兼容以下输入：{@code https://github.com/a/b}、{@code github.com/a/b/}、
     * {@code a/b}、{@code git@github.com:a/b.git}。解析失败返回 null。</p>
     */
    public String resolveRepoPath() {
        if (repoUrl == null) {
            return null;
        }
        String s = repoUrl.trim();
        if (s.isEmpty()) {
            return null;
        }
        // 去协议头与 git@ 前缀
        int idx = s.indexOf("github.com");
        if (idx >= 0) {
            s = s.substring(idx + "github.com".length());
        } else if (s.startsWith("git@")) {
            s = s.substring("git@".length());
        }
        // 此时 s 应形如 "/a/b.git" 或 "a/b"；若已经是 "a/b"（无 github.com）也走这里
        if (s.startsWith(":")) {
            s = s.substring(1);
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        // 去掉尾部路径（issues 等）：只保留前两段
        String[] segs = s.split("/");
        if (segs.length < 2) {
            return null;
        }
        String owner = segs[0].trim();
        String repo = segs[1].trim();
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - ".git".length());
        }
        if (owner.isEmpty() || repo.isEmpty()) {
            return null;
        }
        return owner + "/" + repo;
    }

    /** 配置是否可执行检查（仓库地址能解析出 owner/repo） */
    public boolean isValid() {
        return resolveRepoPath() != null;
    }

    /**
     * 解析实际使用的当前版本号：显式配置优先，否则从宿主 PackageInfo 读取。
     */
    public String resolveCurrentVersion(Context context) {
        if (currentVersion != null && !currentVersion.trim().isEmpty()) {
            return normalizeVersion(currentVersion);
        }
        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                if (pi != null && pi.versionName != null) {
                    return normalizeVersion(pi.versionName);
                }
            } catch (Throwable ignored) {
            }
        }
        return "0";
    }

    /** 归一化版本号：去 v/V 前缀与首尾空白 */
    static String normalizeVersion(String v) {
        if (v == null) {
            return "0";
        }
        String s = v.trim();
        // "v1.4" / "V1.4" → "1.4"（仅当 v 后紧跟数字才剥掉，避免误伤 "ver" 之类）
        if (s.length() > 1 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')
                && Character.isDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        return s;
    }

    // ───────────────────────── 链式配置 ─────────────────────────

    public NokiaUpdateConfig setFallbackUrl(String fallbackUrl) {
        this.fallbackUrl = fallbackUrl;
        return this;
    }

    public NokiaUpdateConfig setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
        return this;
    }

    public NokiaUpdateConfig setIncludePreRelease(boolean includePreRelease) {
        this.includePreRelease = includePreRelease;
        return this;
    }

    public NokiaUpdateConfig setApkAssetKeyword(String apkAssetKeyword) {
        this.apkAssetKeyword = apkAssetKeyword;
        return this;
    }

    public NokiaUpdateConfig setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public String getFallbackUrl() {
        return (fallbackUrl == null || fallbackUrl.trim().isEmpty())
                ? DEFAULT_FALLBACK_URL : fallbackUrl;
    }

    public boolean isIncludePreRelease() {
        return includePreRelease;
    }

    public String getApkAssetKeyword() {
        return apkAssetKeyword;
    }

    public int getTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : 10_000;
    }

    public String getRepoUrl() {
        return repoUrl;
    }
}
