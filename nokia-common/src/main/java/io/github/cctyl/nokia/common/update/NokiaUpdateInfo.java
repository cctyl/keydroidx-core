package io.github.cctyl.nokia.common.update;

/**
 * GitHub Release 解析结果（一次检查拿到的最新版本信息）。
 *
 * <p>字段直接对应 GitHub Releases API 的返回，已做空值与长度归一，
 * 宿主可直接拿来渲染自己的更新界面。</p>
 */
public class NokiaUpdateInfo {

    /** 归一化版本号（tag 去掉 v/V 前缀与首尾空白），如 "1.4.0" */
    public final String version;
    /** GitHub 上原始 tag 名，如 "v1.4.0" */
    public final String tagName;
    /** Release 标题，可能为 null */
    public final String releaseName;
    /** Release 页面地址（浏览器可打开） */
    public final String htmlUrl;
    /** 首个匹配到的 APK 资产下载地址；没有 APK 资产时为 null */
    public final String downloadUrl;
    /** APK 资产文件名；没有 APK 资产时为 null */
    public final String assetName;
    /** APK 资产字节数；未知为 -1 */
    public final long assetSize;
    /** 更新说明（Release body），已裁剪空白；可能为 null */
    public final String changelog;
    /** 发布时间字符串（ISO-8601 原始值）；可能为 null */
    public final String publishTime;

    public NokiaUpdateInfo(String version, String tagName, String releaseName, String htmlUrl,
                           String downloadUrl, String assetName, long assetSize,
                           String changelog, String publishTime) {
        this.version = version;
        this.tagName = tagName;
        this.releaseName = releaseName;
        this.htmlUrl = htmlUrl;
        this.downloadUrl = downloadUrl;
        this.assetName = assetName;
        this.assetSize = assetSize;
        this.changelog = changelog;
        this.publishTime = publishTime;
    }

    /** 该版本是否比传入的当前版本更新 */
    public boolean isNewerThan(String currentVersion) {
        return NokiaUpdateChecker.compareVersion(version, currentVersion) > 0;
    }

    /**
     * 实际可用于下载的 URL：优先 APK 直链，没有 APK 资产时退回 Release 页面。
     */
    public String resolveDownloadUrl() {
        if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
            return downloadUrl;
        }
        return htmlUrl;
    }

    @Override
    public String toString() {
        return "NokiaUpdateInfo{version=" + version + ", tag=" + tagName
                + ", downloadUrl=" + downloadUrl + "}";
    }
}
