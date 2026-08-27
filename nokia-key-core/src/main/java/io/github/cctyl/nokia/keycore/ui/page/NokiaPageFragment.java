package io.github.cctyl.nokia.keycore.ui.page;

import android.view.View;

import androidx.annotation.NonNull;

import io.github.cctyl.nokia.keycore.ui.NokiaFontManager;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.page.NokiaPageFragment}。
 * 此桥接抽象类用于保持对旧包名调用的向后兼容，并内置 Core SDK 默认点阵字体注入。
 */
@Deprecated
public abstract class NokiaPageFragment extends io.github.cctyl.nokia.common.ui.page.NokiaPageFragment {

    @Override
    protected void onApplyFonts(@NonNull View view) {
        super.onApplyFonts(view);
        NokiaFontManager.applyToViewTree(view);
    }
}
