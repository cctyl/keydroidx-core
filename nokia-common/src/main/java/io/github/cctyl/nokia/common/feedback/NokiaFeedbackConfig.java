package io.github.cctyl.nokia.common.feedback;

import java.io.File;

/**
 * 反馈上报全局配置。
 *
 * <p>宿主 APP 在 Application.onCreate 或入口处通过 {@link NokiaFeedback#init(NokiaFeedbackConfig)}
 * 注册一次。服务地址与通信密钥由接入方从服务端分发并注入（值来自宿主 BuildConfig，
 * 绝不入库、不进 SDK）。</p>
 */
public class NokiaFeedbackConfig {

    /** 上传接口完整 URL，如 "http://your.server.com:9421/upload" */
    public final String uploadUrl;
    /**
     * 安装统计上报接口完整 URL，如 "http://your.server.com:9421/install"。
     * <p>为 null 时由 {@link #resolveInstallUrl()} 从 uploadUrl 推导（把末尾 /upload 替换为 /install）。
     * 与 uploadUrl 共用同一服务器、同一鉴权密钥，仅路径不同。</p>
     */
    public final String installUrl;
    /** 通信密钥十六进制字符串（由服务端分发，绝不入库） */
    public final String secretKeyHex;
    /** 应用标识，如 "myapp" */
    public final String appName;
    /** 应用版本名，如 "1.0.0" */
    public final String appVersion;
    /**
     * 自定义日志目录；为 null 时由 SDK 默认取 Context 的标准日志目录
     * （Android/data/<包名>/log，对齐 NokiaLog 与生态规范）
     */
    public final File logDir;

    public NokiaFeedbackConfig(String uploadUrl, String secretKeyHex,
                               String appName, String appVersion, File logDir) {
        this(uploadUrl, null, secretKeyHex, appName, appVersion, logDir);
    }

    public NokiaFeedbackConfig(String uploadUrl, String secretKeyHex,
                               String appName, String appVersion) {
        this(uploadUrl, null, secretKeyHex, appName, appVersion, null);
    }

    /**
     * 完整构造：同时指定反馈上传与安装统计两个接口地址。
     *
     * @param uploadUrl   反馈上传地址（/upload）
     * @param installUrl  安装统计地址（/install）；传 null 则运行时从 uploadUrl 推导
     * @param secretKeyHex 通信密钥（两个接口共用）
     * @param appName     应用标识（两个接口共用）
     * @param appVersion  应用版本名（两个接口共用）
     * @param logDir      日志目录（仅反馈上传使用，安装统计不涉及）
     */
    public NokiaFeedbackConfig(String uploadUrl, String installUrl,
                               String secretKeyHex, String appName,
                               String appVersion, File logDir) {
        this.uploadUrl = uploadUrl;
        this.installUrl = installUrl;
        this.secretKeyHex = secretKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.logDir = logDir;
    }

    /** 旧版 host/port 构造向后兼容 */
    @Deprecated
    public NokiaFeedbackConfig(String host, int port, String secretKeyHex,
                               String appName, String appVersion, File logDir) {
        this((host != null && !host.trim().isEmpty()) ? "http://" + host + ":" + port + "/upload" : "",
                null, secretKeyHex, appName, appVersion, logDir);
    }

    /** 校验配置是否有效（反馈上传 URL 与密钥均不为空） */
    public boolean isValid() {
        return uploadUrl != null && !uploadUrl.trim().isEmpty()
                && secretKeyHex != null && !secretKeyHex.trim().isEmpty()
                && secretKeyHex.trim().length() % 2 == 0;
    }

    /**
     * 解析实际使用的安装统计接口地址。
     * 优先使用显式配置的 installUrl；未配置时从 uploadUrl 推导
     * （将末尾的 {@code /upload} 替换为 {@code /install}）。两者都不可用时返回 null。
     */
    public String resolveInstallUrl() {
        if (installUrl != null && !installUrl.trim().isEmpty()) {
            return installUrl;
        }
        if (uploadUrl == null || uploadUrl.trim().isEmpty()) {
            return null;
        }
        // 末尾是 /upload 就替换为 /install，否则直接在末尾追加 /install
        if (uploadUrl.endsWith("/upload")) {
            return uploadUrl.substring(0, uploadUrl.length() - "/upload".length()) + "/install";
        }
        return uploadUrl.endsWith("/") ? uploadUrl + "install" : uploadUrl + "/install";
    }
}
