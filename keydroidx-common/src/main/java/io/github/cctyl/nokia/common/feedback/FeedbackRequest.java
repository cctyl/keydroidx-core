package io.github.cctyl.nokia.common.feedback;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * 单次反馈请求的不可变值对象。
 */
public final class FeedbackRequest {

    public final String uploadUrl;
    public final String secretKeyHex;
    public final String appName;
    public final String appVersion;
    public final String contact;
    public final String comment;
    public final File logDir;
    public final boolean attachLog;
    public final Map<String, Object> extras;

    public FeedbackRequest(String uploadUrl, String secretKeyHex,
                           String appName, String appVersion,
                           String contact, String comment,
                           File logDir, boolean attachLog,
                           Map<String, Object> extras) {
        this.uploadUrl = uploadUrl;
        this.secretKeyHex = secretKeyHex;
        this.appName = appName;
        this.appVersion = appVersion;
        this.contact = contact;
        this.comment = comment;
        this.logDir = logDir;
        this.attachLog = attachLog;
        this.extras = extras != null ? Collections.unmodifiableMap(extras) : Collections.<String, Object>emptyMap();
    }
}
