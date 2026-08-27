package io.github.cctyl.nokia.keycore.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.dialog.NokiaInputDialog}。
 * <p>此桥接类继承自 {@code nokia-common} 的实现，保留旧包名调用的向后兼容。</p>
 */
@Deprecated
public class NokiaInputDialog extends io.github.cctyl.nokia.common.ui.dialog.NokiaInputDialog {

    public NokiaInputDialog(@NonNull Context context, @NonNull String title, @Nullable String defaultText, @Nullable String hint) {
        super(context, title, defaultText, hint);
    }
}
