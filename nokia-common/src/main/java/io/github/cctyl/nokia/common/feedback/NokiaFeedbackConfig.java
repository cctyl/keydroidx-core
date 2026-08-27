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
        this.uploadUrl = uploadUrl;
        this.secretKeyHex = secretKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.logDir = logDir;
    }

    public NokiaFeedbackConfig(String uploadUrl, String secretKeyHex,
                               String appName, String appVersion) {
        this(uploadUrl, secretKeyHex, appName, appVersion, null);
    }

    /** 旧版 host/port 构造向后兼容 */
    @Deprecated
    public NokiaFeedbackConfig(String host, int port, String secretKeyHex,
                               String appName, String appVersion, File logDir) {
        this((host != null && !host.trim().isEmpty()) ? "http://" + host + ":" + port + "/upload" : "",
                secretKeyHex, appName, appVersion, logDir);
    }

    /** 校验配置是否有效（URL 与密钥均不为空） */
    public boolean isValid() {
        return uploadUrl != null && !uploadUrl.trim().isEmpty()
                && secretKeyHex != null && !secretKeyHex.trim().isEmpty()
                && secretKeyHex.trim().length() % 2 == 0;
    }
}
