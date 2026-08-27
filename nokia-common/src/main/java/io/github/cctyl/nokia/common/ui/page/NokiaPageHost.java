package io.github.cctyl.nokia.common.ui.page;

/**
 * 页面宿主容器接口。
 * <p>
 * 页面（Fragment）通过此接口通知宿主 Activity 刷新顶栏标题与底部软键条，或者请求退出当前页面。
 */
public interface NokiaPageHost {

    /** 请求宿主根据当前可见页面的 {@link NokiaPage} 重新刷新标题与软键条 */
    void refreshPageBar();

    /** 请求退出当前页面（回退栈弹出或关闭宿主 Activity） */
    void exitCurrent();
}
