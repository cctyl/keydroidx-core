package io.github.cctyl.nokia.keycore.ui.page;

/**
 * 诺基亚页面契约接口：声明页面标题与底部三段式软键栏。
 * <p>
 * 页面实现该接口后，由宿主 Activity 在页面切换到前台或页面状态更新时自动装配顶栏标题与底部软键栏，
 * 页面自身无需直接操作底栏各个 TextView。
 * <p>
 * 各 getter 允许动态返回：页面内部状态（焦点、播放状态、弹窗模式等）发生变化后，
 * 调用 {@link NokiaPageHost#refreshPageBar()} 即可通知宿主重新拉取并装配。
 */
public interface NokiaPage extends NokiaFocusHost {

    /**
     * 页面标题（顶栏显示）；返回 null 或空串则保持默认。
     */
    CharSequence getPageTitle();

    /**
     * 左软键文字；返回 null 或空串则隐藏/清空。
     */
    CharSequence getSoftLeftText();

    /**
     * 中软键文字；返回 null 或空串则隐藏/清空。
     */
    CharSequence getSoftCenterText();

    /**
     * 右软键文字；返回 null 或空串则隐藏/清空。
     */
    CharSequence getSoftRightText();
}
