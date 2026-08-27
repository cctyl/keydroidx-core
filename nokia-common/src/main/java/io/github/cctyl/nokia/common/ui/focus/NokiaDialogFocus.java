package io.github.cctyl.nokia.common.ui.focus;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;

/**
 * 弹窗焦点管理工具。
 * <p>
 * 用于在弹窗（Dialog / DialogFragment）显示时强制退出 Touch Mode 并将焦点强行赋予弹窗内的指定视图，
 * 解决 Android 12+ 系统在触控模式下弹窗首次物理按键被系统吞掉的问题。
 */
public class NokiaDialogFocus {

    protected NokiaDialogFocus() {}

    /**
     * 对弹窗强制退出 Touch Mode 并自动寻找第一个可聚焦子视图获焦。
     *
     * @param dialog 目标弹窗
     */
    public static void forceNonTouchMode(@Nullable Dialog dialog) {
        forceNonTouchMode(dialog, null);
    }

    /**
     * 对弹窗强制退出 Touch Mode 并将焦点赋予 targetView。
     *
     * @param dialog     目标弹窗
     * @param targetView 首获焦点的视图（如为空则尝试查找可聚焦子视图）
     */
    public static void forceNonTouchMode(@Nullable Dialog dialog, @Nullable View targetView) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;

        decor.post(() -> {
            if (targetView != null) {
                targetView.setFocusable(true);
                targetView.setFocusableInTouchMode(true);
                targetView.requestFocus();
            } else if (decor instanceof ViewGroup) {
                View firstFocusable = findFirstFocusable((ViewGroup) decor);
                if (firstFocusable != null) {
                    firstFocusable.setFocusable(true);
                    firstFocusable.setFocusableInTouchMode(true);
                    firstFocusable.requestFocus();
                }
            }
        });
    }

    private static View findFirstFocusable(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable()) return child;
            if (child instanceof ViewGroup) {
                View found = findFirstFocusable((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
