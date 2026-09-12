package io.github.cctyl.nokia.common.ui.about;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.ecosystem.KeydroidXApps;
import io.github.cctyl.nokia.common.log.KeydroidxLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 关于页面（KeydroidxAboutFragment）数据配置对象。
 * 支持通过链式 Builder 自由装配应用信息、开源地址、开发者、致谢清单与自定义操作项。
 */
public class KeydroidxAboutConfig implements Serializable {

    private static final String TAG = "KeydroidxAboutConfig";

    private String appName;
    private String versionName;
    private int appIconRes;
    private String description;
    private String author;
    private String repoUrl;
    private String videoUrl;
    private String acknowledgements;
    private String extraStatement;
    private boolean showDetailedLogToggle = true;
    private boolean showUpdateCheck = false;
    private boolean showMoreApps = true;
    /** 「更多应用」清单。createDefault 默认填充「除自己外」的全部生态应用；宿主可覆盖。 */
    private List<KeydroidXApps.App> moreApps = Collections.emptyList();
    /** 可选：覆盖检查更新时使用的「当前版本号」。不设则用 PackageInfo.versionName。
     *  用于宿主在版本号带渠道后缀（如 1.3.1-open）时传入剥干净的逻辑版本号，避免 semver 误判。 */
    private String updateCurrentVersion;
    private final List<LinkItem> extraLinks = new ArrayList<>();

    public static class LinkItem implements Serializable {
        private final String title;
        private final String url;
        private final String glyph;

        public LinkItem(@NonNull String title, @NonNull String url, @Nullable String glyph) {
            this.title = title;
            this.url = url;
            this.glyph = glyph;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getGlyph() { return glyph; }
    }

    public KeydroidxAboutConfig() {}

    /**
     * 自动从 Context 读取 Application 名称、版本号与图标填充默认值。
     */
    public static KeydroidxAboutConfig createDefault(@NonNull Context context) {
        KeydroidxAboutConfig config = new KeydroidxAboutConfig();
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(context.getPackageName(), 0);
            config.appName = pm.getApplicationLabel(appInfo).toString();
            config.appIconRes = appInfo.icon;
        } catch (Exception ignored) {
            KeydroidxLog.w(TAG, "read app info failed: " + ignored.getMessage());
        }

        try {
            PackageInfo pkgInfo = pm.getPackageInfo(context.getPackageName(), 0);
            config.versionName = "v" + pkgInfo.versionName;
        } catch (Exception e) {
            KeydroidxLog.w(TAG, "read package version failed, fallback to v1.0.0: " + e.getMessage());
            config.versionName = "v1.0.0";
        }
        // 默认展示「除自己外」的全部生态应用
        config.moreApps = KeydroidXApps.allExcept(context.getPackageName());
        return config;
    }

    // ─────────────────────────────────────────────────────────────
    //  Getters & Fluent Setters
    // ─────────────────────────────────────────────────────────────

    public String getAppName() { return appName; }
    public KeydroidxAboutConfig setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    public String getVersionName() { return versionName; }
    public KeydroidxAboutConfig setVersionName(String versionName) {
        this.versionName = versionName;
        return this;
    }

    public int getAppIconRes() { return appIconRes; }
    public KeydroidxAboutConfig setAppIconRes(@DrawableRes int appIconRes) {
        this.appIconRes = appIconRes;
        return this;
    }

    public String getDescription() { return description; }
    public KeydroidxAboutConfig setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getAuthor() { return author; }
    public KeydroidxAboutConfig setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getRepoUrl() { return repoUrl; }
    public KeydroidxAboutConfig setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
        return this;
    }

    public String getVideoUrl() { return videoUrl; }
    public KeydroidxAboutConfig setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
        return this;
    }

    public String getAcknowledgements() { return acknowledgements; }
    public KeydroidxAboutConfig setAcknowledgements(String acknowledgements) {
        this.acknowledgements = acknowledgements;
        return this;
    }

    public String getExtraStatement() { return extraStatement; }
    public KeydroidxAboutConfig setExtraStatement(String extraStatement) {
        this.extraStatement = extraStatement;
        return this;
    }

    public boolean isShowDetailedLogToggle() { return showDetailedLogToggle; }
    public KeydroidxAboutConfig setShowDetailedLogToggle(boolean showDetailedLogToggle) {
        this.showDetailedLogToggle = showDetailedLogToggle;
        return this;
    }

    /** 是否在关于页显示「检查更新」卡片（复用 {@link #repoUrl} 作为 GitHub 仓库地址）。 */
    public boolean isShowUpdateCheck() { return showUpdateCheck; }
    public KeydroidxAboutConfig setShowUpdateCheck(boolean showUpdateCheck) {
        this.showUpdateCheck = showUpdateCheck;
        return this;
    }

    public String getUpdateCurrentVersion() { return updateCurrentVersion; }
    public KeydroidxAboutConfig setUpdateCurrentVersion(String updateCurrentVersion) {
        this.updateCurrentVersion = updateCurrentVersion;
        return this;
    }

    public boolean isShowMoreApps() { return showMoreApps; }
    public KeydroidxAboutConfig setShowMoreApps(boolean showMoreApps) {
        this.showMoreApps = showMoreApps;
        return this;
    }

    public List<KeydroidXApps.App> getMoreApps() {
        return moreApps;
    }
    public KeydroidxAboutConfig setMoreApps(@Nullable List<KeydroidXApps.App> moreApps) {
        this.moreApps = (moreApps == null) ? Collections.emptyList() : moreApps;
        return this;
    }

    public List<LinkItem> getExtraLinks() {
        return Collections.unmodifiableList(extraLinks);
    }

    public KeydroidxAboutConfig addExtraLink(@NonNull String title, @NonNull String url, @Nullable String glyph) {
        this.extraLinks.add(new LinkItem(title, url, glyph));
        return this;
    }
}
