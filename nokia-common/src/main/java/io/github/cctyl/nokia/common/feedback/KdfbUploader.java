package io.github.cctyl.nokia.common.feedback;

import java.io.File;
import java.util.Map;

/**
 * @deprecated 协议已升级为 HTTP，请使用 {@link FeedbackUploader}。
 */
@Deprecated
public final class KdfbUploader {

    public static final int MAX_ZIP = FeedbackUploader.MAX_ZIP;
    public static final long MAX_SINGLE_LOG = FeedbackUploader.MAX_SINGLE_LOG;

    private KdfbUploader() {
    }

    public static final class ZipResult {
        public final byte[] zipBytes;
        public final int includedFiles;
        public final int skippedFiles;
        public final long originalTotalBytes;

        public ZipResult(byte[] zipBytes, int includedFiles, int skippedFiles, long originalTotalBytes) {
            this.zipBytes = zipBytes;
            this.includedFiles = includedFiles;
            this.skippedFiles = skippedFiles;
            this.originalTotalBytes = originalTotalBytes;
        }

        public boolean isEmpty() {
            return zipBytes.length == 0;
        }

        public boolean isTrimmed() {
            return skippedFiles > 0;
        }
    }

    public static ZipResult zipLogs(File dir) {
        FeedbackUploader.ZipResult res = FeedbackUploader.zipLogs(dir);
        return new ZipResult(res.zipBytes, res.includedFiles, res.skippedFiles, res.originalTotalBytes);
    }

    public static boolean submit(String host, int port, String privateKeyHex,
                                 String appName, String appVersion,
                                 String contact, String comment,
                                 File logDir, Map<String, Object> extras) {
        String url = "http://" + host + ":" + port + "/upload";
        return FeedbackUploader.submit(url, privateKeyHex, appName, appVersion, contact, comment, logDir, extras);
    }
}
