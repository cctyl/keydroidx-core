package io.github.cctyl.nokia.keycore.feedback;

import java.io.File;

/**
 * 反馈功能全局配置。由宿主 APP 在启动时通过 {@link NokiaFeedback#init(NokiaFeedbackConfig)}
 * 注册一次，值通常来自宿主自己的 BuildConfig（密钥/地址不进 SDK、不入 Git）。
 */
public class NokiaFeedbackConfig {

    /** 服务端地址 */
    public final String host;
    /** 服务端端口 */
    public final int port;
    /** Ed25519 私钥 hex */
    public final String privateKeyHex;
    /** 应用标识（≤32 字符，仅 [a-zA-Z0-9_-]，需与服务端登记一致） */
    public final String appName;
    /** 应用版本号 */
    public final String appVersion;
    /**
     * 日志目录。null 时使用与原键桌面 NokiaLog 一致的统一约定目录：
     * {@code Android/data/<包名>/log}（即 {@code NokiaLog.getDefaultLogDir(Context)}）。
     * 宿主若用 {@link io.github.cctyl.nokia.keycore.log.NokiaLog} 落盘日志，默认即对齐，无需手动指定。
     * 宿主若有自定义日志位置可在此覆盖。
     */
    public final File logDir;

    public NokiaFeedbackConfig(String host, int port, String privateKeyHex,
                               String appName, String appVersion, File logDir) {
        this.host = host;
        this.port = port;
        this.privateKeyHex = privateKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.logDir = logDir;
    }

    public boolean isValid() {
        return host != null && host.length() > 0
                && privateKeyHex != null && privateKeyHex.length() > 0
                && appName != null && appName.length() > 0;
    }
}
