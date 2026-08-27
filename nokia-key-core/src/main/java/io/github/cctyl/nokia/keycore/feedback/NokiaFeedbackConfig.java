package io.github.cctyl.nokia.keycore.feedback;

import java.io.File;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.feedback.NokiaFeedbackConfig}。
 * 此桥接类用于保持对旧包名调用的向后兼容。
 */
@Deprecated
public class NokiaFeedbackConfig extends io.github.cctyl.nokia.common.feedback.NokiaFeedbackConfig {

    public NokiaFeedbackConfig(String uploadUrl, String secretKeyHex,
                               String appName, String appVersion, File logDir) {
        super(uploadUrl, secretKeyHex, appName, appVersion, logDir);
    }

    public NokiaFeedbackConfig(String uploadUrl, String secretKeyHex,
                               String appName, String appVersion) {
        super(uploadUrl, secretKeyHex, appName, appVersion, null);
    }

    public NokiaFeedbackConfig(String host, int port, String privateKeyHex,
                               String appName, String appVersion, File logDir) {
        super(host, port, privateKeyHex, appName, appVersion, logDir);
    }
}
