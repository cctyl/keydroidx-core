package io.github.cctyl.nokia.common.feedback;

import java.io.File;

/**
 * 反馈上报全局配置。
 *
 * <p>宿主 APP 在 Application.onCreate 或入口处通过 {@link KeydroidxFeedback#init(KeydroidxFeedbackConfig)}
 * 注册一次。服务地址与通信密钥由接入方从服务端分发并注入（值来自宿主 BuildConfig，
 * 绝不入库、不进 SDK）。</p>
 *
 * <p>配置中只保留一个 {@code baseUrl}（服务端根地址，如 {@code "https://your.server.com"}），
 * 反馈上传与安装统计两个接口的具体路径由 SDK 内部拼接：</p>
 * <ul>
 *   <li>反馈上传：{@link #resolveUploadUrl()} → {@code baseUrl + "/upload"}</li>
 *   <li>安装统计：{@link #resolveInstallUrl()} → {@code baseUrl + "/install"}</li>
 * </ul>
 */
public class KeydroidxFeedbackConfig {

    /** 服务端根地址，如 "https://your.server.com"（不带任何接口路径） */
    public final String baseUrl;
    /** 通信密钥十六进制字符串（由服务端分发，绝不入库） */
    public final String secretKeyHex;
    /** 应用标识，如 "myapp" */
    public final String appName;
    /** 应用版本名，如 "1.0.0" */
    public final String appVersion;
    /**
     * 自定义日志目录；为 null 时由 SDK 默认取 Context 的标准日志目录
     * （Android/data/<包名>/files/log，对齐 KeydroidxLog 与生态规范）
     */
    public final File logDir;

    public KeydroidxFeedbackConfig(String baseUrl, String secretKeyHex,
                                   String appName, String appVersion, File logDir) {
        this.baseUrl = baseUrl;
        this.secretKeyHex = secretKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.logDir = logDir;
    }

    public KeydroidxFeedbackConfig(String baseUrl, String secretKeyHex,
                                   String appName, String appVersion) {
        this(baseUrl, secretKeyHex, appName, appVersion, null);
    }

    /** 旧版 host/port 构造向后兼容 */
    @Deprecated
    public KeydroidxFeedbackConfig(String host, int port, String secretKeyHex,
                                   String appName, String appVersion, File logDir) {
        this((host != null && !host.trim().isEmpty()) ? "http://" + host + ":" + port : "",
                secretKeyHex, appName, appVersion, logDir);
    }

    /** 校验配置是否有效（baseUrl 与密钥均不为空） */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.trim().isEmpty()
                && secretKeyHex != null && !secretKeyHex.trim().isEmpty()
                && secretKeyHex.trim().length() % 2 == 0;
    }

    /**
     * 解析实际使用的反馈上传接口地址：{@code baseUrl + "/upload"}。
     * baseUrl 为空时返回 null。
     */
    public String resolveUploadUrl() {
        return joinPath(baseUrl, "/upload");
    }

    /**
     * 解析实际使用的安装统计接口地址：{@code baseUrl + "/install"}。
     * baseUrl 为空时返回 null。
     */
    public String resolveInstallUrl() {
        return joinPath(baseUrl, "/install");
    }

    /**
     * 把 {@code path}（以 {@code /} 开头）拼接到 {@code baseUrl} 之后，
     * 自动消解中间多余/缺失的斜杠：
     * <ul>
     *   <li>{@code joinPath("http://h:1",    "/upload")} → {@code "http://h:1/upload"}</li>
     *   <li>{@code joinPath("http://h:1/",   "/upload")} → {@code "http://h:1/upload"}</li>
     *   <li>{@code joinPath("http://h:1/base", "/upload")} → {@code "http://h:1/base/upload"}</li>
     * </ul>
     * baseUrl 为 null/空、path 为 null/空时返回 null。
     */
    private static String joinPath(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) return null;
        if (path == null || path.isEmpty()) return baseUrl;
        // 去掉 baseUrl 末尾的斜杠，path 以斜杠开头，直接拼接
        String b = baseUrl;
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }
}
