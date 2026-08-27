package io.github.cctyl.nokia.common.feedback;

import java.io.File;
import java.util.Map;

/** 一次反馈提交所需的全部参数（自定义 UI 时使用）。 */
public class FeedbackRequest {

    /** 服务端地址与端口 */
    public final String host;
    public final int port;
    /** Ed25519 私钥 hex */
    public final String privateKeyHex;
    /** 应用标识（≤32 字符，仅 [a-zA-Z0-9_-]） */
    public final String appName;
    /** 应用版本号 */
    public final String appVersion;
    /** 用户联系方式（必填，≤100 字符） */
    public final String contact;
    /** 问题描述（必填，≤500 字符） */
    public final String comment;
    /** 日志目录（选填）；目录不存在或为 null 时不附带日志 */
    public final File logDir;
    /** 是否附带日志 */
    public final boolean attachLog;
    /** 额外字段（选填），null 时自动填充设备信息 */
    public final Map<String, Object> extras;

    public FeedbackRequest(String host, int port, String privateKeyHex,
                           String appName, String appVersion,
                           String contact, String comment,
                           File logDir, boolean attachLog,
                           Map<String, Object> extras) {
        this.host = host;
        this.port = port;
        this.privateKeyHex = privateKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.contact = contact;
        this.comment = comment;
        this.logDir = logDir;
        this.attachLog = attachLog;
        this.extras = extras;
    }

    /** 校验必填项是否就绪 */
    public boolean isConfigValid() {
        return host != null && host.length() > 0 && privateKeyHex != null && privateKeyHex.length() > 0;
    }
}
