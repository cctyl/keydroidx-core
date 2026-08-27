package io.github.cctyl.nokia.keycore.ui.page;

import android.content.Context;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.page.NokiaListFocusHelper}。
 * 此桥接类用于保持对旧包名调用的向后兼容。
 */
@Deprecated
public class NokiaListFocusHelper extends io.github.cctyl.nokia.common.ui.page.NokiaListFocusHelper {

    public NokiaListFocusHelper() {
        super();
    }

    public NokiaListFocusHelper(@Nullable ScrollView scrollView) {
        super(scrollView);
    }

    public NokiaListFocusHelper(@Nullable Context context, @Nullable ScrollView scrollView) {
        super(context, scrollView);
    }
}
