package io.github.cctyl.nokia.keycore.ui.page;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.ui.NokiaTheme;

/**
 * 纵向列表页 Fragment 基类（模板方法模式，强制循环导航 + 焦点管理）。
 * <p>
 * 专注于「纵向单列菜单/列表」页面，收编了各页面重复手写的焦点管理三件套：
 * <ul>
 *   <li>{@link #setFocusIndex(int)}：清除旧高亮 → 更新索引 → 绘制新高亮 → 自动平滑滚动到可视区；</li>
 *   <li>{@link #onDirection(int)}：<b>强制循环导航</b>（首项按上到末尾，末项按下到开头）；</li>
 *   <li>{@link #onLeftRight(int)}：左右方向键钩子（默认消费，子类可覆写切 Tab 等）。</li>
 * </ul>
 * 子类只需在 {@link #onPageCreated(View, Bundle)} 中填充 {@link #itemViews} 和
 * {@link #listScroll}，其余导航/焦点/滚动全部继承生效。
 */
public abstract class NokiaListPageFragment extends NokiaPageFragment {

    /**
     * 列表项视图数组，子类在 {@link #onPageCreated} 中填充。
     */
    protected View[] itemViews;

    /**
     * 列表的 ScrollView 容器，子类在 {@link #onPageCreated} 中赋值。
     */
    protected ScrollView listScroll;

    /**
     * 当前焦点索引。
     */
    protected int focusIndex = -1;

    /**
     * 当前高亮选中的视图（用于清除旧高亮）。
     */
    private View selectedView;

    /**
     * 子类可覆写此方法临时禁用方向键导航（如弹窗态）。
     * 返回 false 时方向键仍被消费（不穿透），但焦点不移动。
     */
    protected boolean isDirectionEnabled() {
        return true;
    }

    /**
     * 列表项数量。默认从 {@link #itemViews} 长度获取。
     */
    protected int getItemCount() {
        return itemViews != null ? itemViews.length : 0;
    }

    /**
     * 快捷设置列表项（支持 List）。
     */
    protected void setItemList(@Nullable List<View> views) {
        if (views != null) {
            itemViews = views.toArray(new View[0]);
        } else {
            itemViews = null;
        }
    }

    // ---- 方向键导航（final，禁止绕过循环导航规范） ----

    @Override
    public final boolean onDirection(int direction) {
        if (!isDirectionEnabled()) return true;
        int count = getItemCount();
        if (count == 0) return false;

        if (focusIndex < 0) {
            setFocusIndex(0);
            return true;
        }

        switch (direction) {
            case NokiaKeyAction.UP:
                setFocusIndex(focusIndex > 0 ? focusIndex - 1 : count - 1);
                return true;
            case NokiaKeyAction.DOWN:
                setFocusIndex(focusIndex < count - 1 ? focusIndex + 1 : 0);
                return true;
            case NokiaKeyAction.LEFT:
            case NokiaKeyAction.RIGHT:
                return onLeftRight(direction);
            default:
                return false;
        }
    }

    /**
     * 左右方向键的处理钩子。<b>默认消费（返回 true，无效果）</b>。
     * 子类可覆写实现左右切页签等逻辑。
     *
     * @param direction {@link NokiaKeyAction#LEFT} 或 {@link NokiaKeyAction#RIGHT}
     * @return true 表示已消费该事件
     */
    protected boolean onLeftRight(int direction) {
        return true;
    }

    // ---- 焦点管理 ----

    /**
     * 设置焦点到指定索引项：清除旧高亮 → 更新焦点索引 → 应用新高亮 → 自动滚动到可视区。
     */
    protected void setFocusIndex(int index) {
        if (itemViews == null || index < 0 || index >= itemViews.length) return;
        clearFocusBackground();
        focusIndex = index;
        applyFocusBackground();
        scrollToVisible(index);
    }

    /**
     * 清除当前高亮状态。
     */
    protected void clearFocusBackground() {
        if (selectedView != null) {
            selectedView.setBackgroundResource(0);
            selectedView = null;
        }
    }

    /**
     * 应用当前索引项的高亮背景。
     */
    protected void applyFocusBackground() {
        if (focusIndex >= 0 && itemViews != null && focusIndex < itemViews.length) {
            View view = itemViews[focusIndex];
            if (view != null && getContext() != null) {
                view.setBackground(NokiaTheme.createSelectionDrawable(getContext(), 4));
                selectedView = view;
                view.requestFocus();
            }
        }
    }

    // ---- 滚动跟随 ----

    /**
     * 确保焦点行在 ScrollView 可视区域内。
     */
    protected void scrollToVisible(int index) {
        if (listScroll == null || itemViews == null
                || index < 0 || index >= itemViews.length) return;
        smoothScrollToVisible(listScroll, itemViews[index]);
    }

    /**
     * 约束 ScrollView 高度，使其底部正好落在中间面板可视区底边。
     */
    protected void constrainScrollHeight(@NonNull View root, @NonNull ScrollView scroll) {
        root.post(() -> {
            View parent = (View) root.getParent();
            if (!(parent instanceof View)) return;
            int panelH = parent.getHeight();
            float scale = root.getScaleX();
            if (scale <= 0) scale = 1;
            int visibleH = (int) (panelH / scale);
            int headH = scroll.getTop();
            int scrollH = visibleH - headH;
            if (scrollH > 0) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = scrollH;
                scroll.setLayoutParams(lp);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        itemViews = null;
        listScroll = null;
        selectedView = null;
        focusIndex = -1;
    }
}
