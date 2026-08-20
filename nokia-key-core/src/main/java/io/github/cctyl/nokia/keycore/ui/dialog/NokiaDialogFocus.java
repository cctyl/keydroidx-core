package io.github.cctyl.nokia.keycore.ui.dialog;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;

/**
 * Dialog 弹窗按键焦点助手。
 *
 * <p>解决 Android 12+ 上 Dialog 窗口默认处于触摸模式（touch mode）导致第一个按键被吞的系统问题。</p>
 */
public final class NokiaDialogFocus {

    private NokiaDialogFocus() {}

    public static void forceNonTouchMode(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        final View decor = dialog.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        decor.setFocusable(true);
        decor.setFocusableInTouchMode(true);
        if (decor instanceof ViewGroup) {
            ((ViewGroup) decor).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        decor.post(decor::requestFocus);
    }
}
