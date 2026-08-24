package io.github.cctyl.nokia.keycore.ui.page;

/**
 * 页面宿主容器契约（由宿主 Activity 实现）。
 * <p>
 * 供托管在 Activity 内的 {@link NokiaPageFragment} 或独立 Controller 与宿主骨架进行通信。
 */
public interface NokiaPageHost {

    /**
     * 刷新顶栏标题与底部软键栏（重新拉取当前前台 {@link NokiaPage} 的声明）。
     */
    void refreshPageBar();

    /**
     * 设置顶栏页面标题。
     */
    void setPageTitle(CharSequence title);

    /**
     * 设置底部三段式软键文字。
     */
    void setSoftKeys(CharSequence left, CharSequence center, CharSequence right);

    /**
     * 退出当前页面或返回上一级（通常触发 Activity finish 或 Fragment 出栈）。
     */
    void exitCurrent();
}
