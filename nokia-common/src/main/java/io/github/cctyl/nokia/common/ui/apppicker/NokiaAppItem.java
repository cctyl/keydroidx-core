package io.github.cctyl.nokia.common.ui.apppicker;

import android.graphics.drawable.Drawable;

/**
 * 应用选择器中的应用项实体
 */
public class NokiaAppItem {
    private final String packageName;
    private final String appName;
    private final Drawable icon;

    public NokiaAppItem(String packageName, String appName, Drawable icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public Drawable getIcon() {
        return icon;
    }
}
