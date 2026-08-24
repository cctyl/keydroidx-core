package io.github.cctyl.nokia.keycore.ui.page;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.ui.NokiaTheme;

/**
 * 列表焦点与循环导航控制器。
 * <p>
 * 独立组件，解耦于 Activity/Fragment 继承关系。
 * 提供标准按键机体验：
 * <ul>
 *   <li>焦点上下循环导航（首项按上到末尾，末项按下到开头）；</li>
 *   <li>高亮背景自动设置与清除（默认使用生态主题色高亮矩形）；</li>
 *   <li>自动防出界可视区平滑滚动（{@code smoothScrollToVisible}）；</li>
 *   <li>支持静态 View 数组与动态 View 列表。</li>
 * </ul>
 */
public class NokiaListFocusHelper {

    public interface OnFocusChangedListener {
        void onFocusChanged(int oldIndex, int newIndex, @Nullable View view);
    }

    private final List<View> items = new ArrayList<>();
    private ScrollView scrollView;
    private int focusIndex = -1;
    private View selectedView;
    private boolean cyclic = true;
    private boolean directionEnabled = true;
    private OnFocusChangedListener listener;

    public NokiaListFocusHelper() {
    }

    public NokiaListFocusHelper(@Nullable ScrollView scrollView) {
        this.scrollView = scrollView;
    }

    public NokiaListFocusHelper(@Nullable Context context, @Nullable ScrollView scrollView) {
        this.scrollView = scrollView;
    }

    /**
     * 绑定 ScrollView 滚动容器。
     */
    public NokiaListFocusHelper setScrollView(@Nullable ScrollView scrollView) {
        this.scrollView = scrollView;
        return this;
    }

    /**
     * 设置是否开启首尾循环导航（默认 true）。
     */
    public NokiaListFocusHelper setCyclic(boolean cyclic) {
        this.cyclic = cyclic;
        return this;
    }

    /**
     * 临时启用或禁用方向键导航（如弹出二级弹窗时）。
     */
    public void setDirectionEnabled(boolean enabled) {
        this.directionEnabled = enabled;
    }

    public boolean isDirectionEnabled() {
        return directionEnabled;
    }

    public void setOnFocusChangedListener(@Nullable OnFocusChangedListener listener) {
        this.listener = listener;
    }

    /**
     * 设置条目视图列表。
     */
    public void setItems(@Nullable List<View> newItems) {
        clearFocus();
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
    }

    /**
     * 设置条目视图数组。
     */
    public void setItems(@Nullable View[] newItems) {
        clearFocus();
        items.clear();
        if (newItems != null) {
            items.addAll(Arrays.asList(newItems));
        }
    }

    /**
     * 添加条目视图。
     */
    public void addItem(@NonNull View view) {
        items.add(view);
    }

    /**
     * 清空条目。
     */
    public void clearItems() {
        clearFocus();
        items.clear();
    }

    public int getItemCount() {
        return items.size();
    }

    public List<View> getItems() {
        return items;
    }

    @Nullable
    public View getItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return null;
    }

    public int getFocusIndex() {
        return focusIndex;
    }

    @Nullable
    public View getFocusedView() {
        if (focusIndex >= 0 && focusIndex < items.size()) {
            return items.get(focusIndex);
        }
        return null;
    }

    /**
     * 清除当前高亮与焦点。
     */
    public void clearFocus() {
        if (selectedView != null) {
            selectedView.setBackgroundResource(0);
            selectedView = null;
        }
        int old = focusIndex;
        focusIndex = -1;
        if (old != -1 && listener != null) {
            listener.onFocusChanged(old, -1, null);
        }
    }

    /**
     * 设置焦点到指定索引。
     *
     * @param index 目标索引（越界将被安全截断或忽略）
     * @return true 表示焦点成功更新
     */
    public boolean setFocusIndex(int index) {
        return setFocusIndex(index, true);
    }

    /**
     * 设置焦点到指定索引，并选择是否自动平滑滚动到可视区。
     */
    public boolean setFocusIndex(int index, boolean autoScroll) {
        if (items.isEmpty()) {
            clearFocus();
            return false;
        }
        if (index < 0 || index >= items.size()) {
            return false;
        }
        if (selectedView != null) {
            selectedView.setBackgroundResource(0);
        }
        int oldIndex = focusIndex;
        focusIndex = index;
        selectedView = items.get(index);
        if (selectedView != null) {
            Context ctx = selectedView.getContext();
            if (ctx != null) {
                selectedView.setBackground(NokiaTheme.createSelectionDrawable(ctx, 4));
            }
            selectedView.requestFocus();
            if (autoScroll && scrollView != null) {
                smoothScrollToVisible(scrollView, selectedView);
            }
        }
        if (listener != null) {
            listener.onFocusChanged(oldIndex, focusIndex, selectedView);
        }
        return true;
    }

    /**
     * 处理方向键导航事件。
     *
     * @param direction 为 {@link NokiaKeyAction#UP}、{@link NokiaKeyAction#DOWN} 等
     * @return true 表示已消费该事件
     */
    public boolean onDirection(int direction) {
        if (!directionEnabled) return true;
        int count = items.size();
        if (count == 0) return false;

        if (focusIndex < 0) {
            setFocusIndex(0);
            return true;
        }

        if (direction == NokiaKeyAction.UP) {
            if (focusIndex > 0) {
                setFocusIndex(focusIndex - 1);
            } else if (cyclic) {
                setFocusIndex(count - 1);
            }
            return true;
        } else if (direction == NokiaKeyAction.DOWN) {
            if (focusIndex < count - 1) {
                setFocusIndex(focusIndex + 1);
            } else if (cyclic) {
                setFocusIndex(0);
            }
            return true;
        }
        return false;
    }

    /**
     * 通用滚动跟随辅助方法：确保目标子视图 {@code target} 完全处于 {@code scroll} 的可视区内。
     * 自动循环累加父级视图的 top 偏移量，计算出相对于 ScrollView 的真实垂直坐标并平滑滚动。
     */
    public static void smoothScrollToVisible(@Nullable ScrollView scroll, @Nullable View target) {
        if (scroll == null || target == null) return;
        scroll.post(() -> {
            int scrollY = scroll.getScrollY();
            int itemTop = 0;
            View current = target;
            while (current != null && current != scroll && current.getParent() instanceof View) {
                itemTop += current.getTop();
                current = (View) current.getParent();
            }
            int itemBottom = itemTop + target.getHeight();
            int svHeight = scroll.getHeight();
            if (svHeight <= 0) return;
            if (itemTop < scrollY) {
                scroll.smoothScrollTo(0, itemTop);
            } else if (itemBottom > scrollY + svHeight) {
                scroll.smoothScrollTo(0, itemBottom - svHeight);
            }
        });
    }
}
