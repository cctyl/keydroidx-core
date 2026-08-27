package io.github.cctyl.nokia.common.ui.page;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.model.NokiaKeyAction;

/**
 * 诺基亚滚动页面 Fragment 抽象基类。
 * <p>
 * 适用于包含 {@link ScrollView} 的文本说明页、长表单页、非纯列表视图等。
 * 基类会自动在根布局中递归查找第一个 ScrollView。
 * <p>
 * 默认实现了 {@link #onDirection(int)}：当接收到上下按键时，按比例步长平滑滚动。
 */
public abstract class NokiaScrollPageFragment extends NokiaPageFragment {

    protected ScrollView pageScrollView;

    @Override
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        pageScrollView = findScrollView(view);
        onScrollPageCreated(view, savedInstanceState);
    }

    /**
     * 子类页面初始化钩子，替代 {@link #onPageCreated(View, Bundle)}。
     */
    protected void onScrollPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    }

    /**
     * 递归查找布局中的 ScrollView。
     */
    @Nullable
    protected ScrollView findScrollView(@Nullable View root) {
        if (root instanceof ScrollView) {
            return (ScrollView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView sv = findScrollView(group.getChildAt(i));
                if (sv != null) {
                    return sv;
                }
            }
        }
        return null;
    }

    /**
     * 获取上下方向键单次滚动的步长（像素）。默认每次滚动可视高度的 45%。
     */
    protected int getScrollStepPx() {
        if (pageScrollView != null && pageScrollView.getHeight() > 0) {
            return (int) (pageScrollView.getHeight() * 0.45f);
        }
        if (getContext() != null) {
            return (int) (100 * getResources().getDisplayMetrics().density);
        }
        return 160;
    }

    /**
     * 向上平滑滚动一个步长。
     */
    public boolean scrollUp() {
        if (pageScrollView != null) {
            int step = getScrollStepPx();
            pageScrollView.smoothScrollBy(0, -step);
            return true;
        }
        return false;
    }

    /**
     * 向下平滑滚动一个步长。
     */
    public boolean scrollDown() {
        if (pageScrollView != null) {
            int step = getScrollStepPx();
            pageScrollView.smoothScrollBy(0, step);
            return true;
        }
        return false;
    }

    public boolean canScrollUp() {
        return pageScrollView != null && pageScrollView.canScrollVertically(-1);
    }

    public boolean canScrollDown() {
        return pageScrollView != null && pageScrollView.canScrollVertically(1);
    }

    @Override
    public boolean onDirection(int action) {
        if (action == NokiaKeyAction.UP) {
            return scrollUp();
        } else if (action == NokiaKeyAction.DOWN) {
            return scrollDown();
        }
        return false;
    }
}
