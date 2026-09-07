package io.github.cctyl.nokia.common.ecosystem;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * KeydroidX 生态应用注册表。
 * <p>
 * common 内置一份统一的姊妹应用清单（名称、base applicationId、仓库地址、简介），
 * 供「更多应用」页面展示。各宿主关于页默认展示「除自己外」的全部应用，
 * 新增应用时只需在此处登记一行，三端关于页自动同步。
 * <p>
 * 同时内置生态统一的网盘地址常量，作为「更多应用」页底部的额外下载入口。
 */
public final class KeydroidXApps {

    private KeydroidXApps() {}

    /** 生态应用条目。 */
    public static final class App implements Serializable {
        private final String name;
        /** base applicationId；用于「除自己外」过滤（兼容带后缀的变体，如 .debug）。 */
        private final String packageName;
        private final String repoUrl;
        private final String desc;

        public App(@NonNull String name, @NonNull String packageName,
                   @NonNull String repoUrl, @NonNull String desc) {
            this.name = name;
            this.packageName = packageName;
            this.repoUrl = repoUrl;
            this.desc = desc;
        }

        public String getName() { return name; }
        public String getPackageName() { return packageName; }
        public String getRepoUrl() { return repoUrl; }
        public String getDesc() { return desc; }
    }

    // ─────────────────────────────────────────────────────────────
    //  统一网盘入口
    // ─────────────────────────────────────────────────────────────

    /** 「更多应用」页底部展示的统一网盘地址。 */
    public static final String PAN_URL =
            "https://pan.baidu.com/s/1fA-sloEx9ERN-mK8wVXKzQ?pwd=fe66";
    public static final String PAN_LABEL = "百度网盘";
    public static final String PAN_CODE = "fe66";

    // ─────────────────────────────────────────────────────────────
    //  内置应用清单
    // ─────────────────────────────────────────────────────────────
    //  注意：packageName 用 base applicationId（不含 flavor/debug 后缀），
    //  过滤时按前缀匹配（selfPkg.equals(base) || selfPkg.startsWith(base + ".")），
    //  这样 debug 变体（如 io.github.cctyl.nokia.debug）也能正确过滤掉自己。
    // ─────────────────────────────────────────────────────────────

    private static final List<App> APPS = Collections.unmodifiableList(Arrays.asList(
            new App("KeydroidX Launcher（原键桌面）", "io.github.cctyl.nokia",
                    "https://github.com/cctyl/keydroidx-launcher",
                    "诺基亚风格的全功能安卓桌面启动器"),
            new App("KeydroidX Music", "io.github.cctyl.keydroidx.music",
                    "https://github.com/cctyl/keydroidx-music",
                    "物理按键优先的极简音乐播放器"),
            new App("KeydroidX Focus", "io.github.cctyl.keydroidx.focus",
                    "https://github.com/cctyl/keydroidx-foucs",
                    "复古按键友好的专注 / 番茄钟")
    ));

    /** 全部生态应用（不可变）。 */
    public static List<App> all() {
        return APPS;
    }

    /** 除指定包名（含其带后缀的变体）外的全部应用。 */
    @NonNull
    public static List<App> allExcept(@NonNull String selfPackage) {
        List<App> out = new ArrayList<>();
        for (App a : APPS) {
            if (!matches(a.getPackageName(), selfPackage)) {
                out.add(a);
            }
        }
        return out;
    }

    private static boolean matches(@NonNull String base, @NonNull String selfPackage) {
        return selfPackage.equals(base) || selfPackage.startsWith(base + ".");
    }
}
