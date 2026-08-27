package io.github.cctyl.nokia.keycore.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog}。
 * <p>此桥接类继承自 {@code nokia-common} 的实现，保留旧包名调用的向后兼容；
 * 按键解析与主题由宿主 {@code NokiaBaseActivity}（实现 {@code KeyResolver} /
 * {@code ThemeProvider}）提供，桌面 Launcher 与独立 App 共享同一份实现。</p>
 */
@Deprecated
public class NokiaOptionsDialog extends io.github.cctyl.nokia.common.ui.dialog.NokiaOptionsDialog {

    public NokiaOptionsDialog(@NonNull Context context) {
        super(context);
    }

    public NokiaOptionsDialog(@NonNull Context context, @NonNull String title) {
        super(context, title);
    }
}
