package io.github.cctyl.nokia.common.ui.page;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * 页面宿主容器接口。
 * <p>
 * 页面（Fragment）通过此接口通知宿主 Activity 刷新顶栏标题与底部软键条，或者请求退出当前页面，
 * 或打开一个子页面并加入返回栈（供关于页等内部页面导航）。
 */
public interface NokiaPageHost {

    /** 请求宿主根据当前可见页面的 {@link NokiaPage} 重新刷新标题与软键条 */
    void refreshPageBar();

    /** 请求退出当前页面（回退栈弹出或关闭宿主 Activity） */
    void exitCurrent();

    /**
     * 打开一个子页面并加入返回栈。
     * <p>
     * 供内部页面（如关于页）向更深层页面（如「更多应用」列表页）导航。
     * 宿主负责 {@code replace} 中间面板容器并 {@code addToBackStack}。
     *
     * @param fragment 要打开的子页面
     */
    void openFragment(@NonNull Fragment fragment);
}
