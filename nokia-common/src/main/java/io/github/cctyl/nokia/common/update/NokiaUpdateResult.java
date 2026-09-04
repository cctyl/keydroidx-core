package io.github.cctyl.nokia.common.update;

/**
 * 一次检查更新的结果。
 *
 * <p>三种状态：</p>
 * <ul>
 *   <li>{@link Status#UPDATE_AVAILABLE}：远端有更新版本，{@link #info} 非空；</li>
 *   <li>{@link Status#UP_TO_DATE}：已是最新版本，{@link #info} 为远端版本（可为空）；</li>
 *   <li>{@link Status#FAILED}：网络失败 / 响应异常，{@link #error} 描述原因，
 *       建议引导用户前往 {@link NokiaUpdateConfig#getFallbackUrl()}。</li>
 * </ul>
 */
public class NokiaUpdateResult {

    public enum Status {
        UPDATE_AVAILABLE, UP_TO_DATE, FAILED
    }

    public final Status status;
    /** 最新版本信息；UP_TO_DATE 且网络正常时也携带，FAILED 时为 null */
    public final NokiaUpdateInfo info;
    /** 当前版本号（归一化后） */
    public final String currentVersion;
    /** 失败原因描述；仅 FAILED 时非空 */
    public final String error;

    NokiaUpdateResult(Status status, NokiaUpdateInfo info, String currentVersion, String error) {
        this.status = status;
        this.info = info;
        this.currentVersion = currentVersion;
        this.error = error;
    }

    static NokiaUpdateResult available(NokiaUpdateInfo info, String currentVersion) {
        return new NokiaUpdateResult(Status.UPDATE_AVAILABLE, info, currentVersion, null);
    }

    static NokiaUpdateResult upToDate(NokiaUpdateInfo info, String currentVersion) {
        return new NokiaUpdateResult(Status.UP_TO_DATE, info, currentVersion, null);
    }

    static NokiaUpdateResult failed(String error, String currentVersion) {
        return new NokiaUpdateResult(Status.FAILED, null, currentVersion, error);
    }

    @Override
    public String toString() {
        return "NokiaUpdateResult{status=" + status + ", current=" + currentVersion
                + ", error=" + error + ", info=" + info + "}";
    }
}
