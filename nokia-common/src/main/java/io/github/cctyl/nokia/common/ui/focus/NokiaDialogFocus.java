package io.github.cctyl.nokia.common.ui.focus;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;

/**
 * 弹窗焦点管理工具。
 *
 * <p>在 Android 12+ 上，新建的 Dialog 独立窗口常处于触摸模式（touch mode），
 * 导致第一个导航键（方向键/确认键等）被 {@code ViewRootImpl} 消费用于退出触摸模式，
 * 弹窗对首个按键无响应、第二次按下才生效。</p>
 *
 * <p>本工具在弹窗 show 后让 DecorView 自身持有焦点并阻止后代接管焦点，
 * 使 {@code ViewRootImpl.leaveTouchMode()} 返回 false，从而放行第一个按键。
 * 该方案来源于原键桌面的实战修复，比"查找第一个可聚焦子视图"更可靠：
 * 不依赖弹窗内容里存在可聚焦的子 View。</p>
 */
public class NokiaDialogFocus {

    protected NokiaDialogFocus() {}

    /**
     * 在弹窗显示后强制其窗口退出"会吞第一个导航键"的状态。
     * 应在弹窗 {@code onCreateDialog} 中通过 {@code setOnShowListener} 调用。
     *
     * @param dialog 目标弹窗
     */
    public static void forceNonTouchMode(@Nullable Dialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;
        decor.setFocusable(true);
        decor.setFocusableInTouchMode(true);
        // FOCUS_BLOCK_DESCENDANTS：让 leaveTouchMode() 认为"已聚焦的 ViewGroup 不倾向让后代
        // 接管焦点"，从而返回 false，第一个导航键不再被吞。
        if (decor instanceof ViewGroup) {
            ((ViewGroup) decor).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        decor.post(decor::requestFocus);
    }

    /**
     * 重载：弹窗显示后将焦点赋予 targetView（显式指定目标视图时使用子视图获焦方式）。
     *
     * @param dialog     目标弹窗
     * @param targetView 首获焦点的视图（为空时行为同 {@link #forceNonTouchMode(Dialog)}）
     */
    public static void forceNonTouchMode(@Nullable Dialog dialog, @Nullable View targetView) {
        if (targetView == null) {
            forceNonTouchMode(dialog);
            return;
        }
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;
        decor.post(() -> {
            targetView.setFocusable(true);
            targetView.setFocusableInTouchMode(true);
            targetView.requestFocus();
        });
    }
}
