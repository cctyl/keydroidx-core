package io.github.cctyl.nokia.keycore.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.dialog.NokiaConfirmDialog}。
 * <p>此桥接类继承自 {@code nokia-common} 的实现，保留旧包名调用的向后兼容。</p>
 */
@Deprecated
public class NokiaConfirmDialog extends io.github.cctyl.nokia.common.ui.dialog.NokiaConfirmDialog {

    public NokiaConfirmDialog(@NonNull Context context, @NonNull String title, @NonNull String message) {
        super(context, title, message);
    }
}
