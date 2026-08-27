package io.github.cctyl.nokia.common.ui.page;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 诺基亚页面 Fragment 抽象基类（模板方法模式）。
 * <p>
 * 为生态中的页面提供生命周期固化：
 * <ol>
 *   <li>{@link #onCreateView}：自动 inflate {@link #getLayoutRes()} 声明的布局；</li>
 *   <li>{@link #onViewCreated}：通知宿主装配顶栏与软键栏、回调 {@link #onPageCreated}；</li>
 *   <li>内置生命周期安全的 {@link #smoothScrollToVisible} 滚动跟随方法。</li>
 * </ol>
 */
public abstract class NokiaPageFragment extends Fragment implements NokiaPage {

    /**
     * 子类声明页面布局资源 ID。
     */
    @LayoutRes
    protected abstract int getLayoutRes();

    /**
     * 是否贴容器顶部。true 为贴顶（默认）；false 为居中。
     */
    protected boolean isTopAlign() {
        return true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(getLayoutRes(), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(0);

        // 通知宿主刷新顶栏和软键栏
        notifyHostRefresh();

        // 子类业务视图与数据初始化钩子
        onPageCreated(view, savedInstanceState);

        // 字体应用钩子（子类或上层框架重写以注入自定义点阵字体）
        onApplyFonts(view);
    }

    /**
     * 字体应用钩子。默认空实现，上层模块可在此递归应用自定义字体。
     */
    protected void onApplyFonts(@NonNull View view) {
    }

    /**
     * 页面初始化钩子。子类在此处进行 findViewById、设置监听与加载数据。
     */
    protected void onPageCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    }

    /**
     * 通知宿主 Activity 重新拉取本页面声明的标题与软键栏。
     */
    public void notifyHostRefresh() {
        Activity activity = getActivity();
        if (activity instanceof NokiaPageHost) {
            ((NokiaPageHost) activity).refreshPageBar();
        }
    }

    /**
     * 通用滚动跟随辅助方法：确保目标子视图 {@code target} 完全处于 {@code scroll} 的可视区内。
     */
    public void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target) {
        if (scroll == null || target == null) return;
        scroll.post(() -> {
            if (scroll == null || !isAdded()) return;
            NokiaListFocusHelper.smoothScrollToVisible(scroll, target);
        });
    }

    // ---- NokiaPage 默认契约实现（子类按需重写） ----

    @Override
    public CharSequence getPageTitle() {
        return null;
    }

    @Override
    public CharSequence getSoftLeftText() {
        return null;
    }

    @Override
    public CharSequence getSoftCenterText() {
        return null;
    }

    @Override
    public CharSequence getSoftRightText() {
        return "返回";
    }

    @Override
    public boolean onDirection(int direction) {
        return false;
    }

    @Override
    public boolean onSelect() {
        return false;
    }

    @Override
    public boolean onSoftLeft() {
        return false;
    }

    @Override
    public boolean onSoftRight() {
        return onBack();
    }

    @Override
    public boolean onBack() {
        Activity activity = getActivity();
        if (activity instanceof NokiaPageHost) {
            ((NokiaPageHost) activity).exitCurrent();
            return true;
        } else if (activity != null) {
            activity.onBackPressed();
            return true;
        }
        return false;
    }
}
