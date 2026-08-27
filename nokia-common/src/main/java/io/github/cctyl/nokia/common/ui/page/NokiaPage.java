package io.github.cctyl.nokia.common.ui.page;

import io.github.cctyl.nokia.common.ui.focus.NokiaFocusHost;

/**
 * 诺基亚复古页面契约接口。
 * <p>
 * 约定页面的标题和底部三个软键的文案，并继承 {@link NokiaFocusHost} 具备按键分发能力。
 */
public interface NokiaPage extends NokiaFocusHost {

    /** 获取页面标题（显示在顶栏中间），返回 null 时不修改当前标题 */
    CharSequence getPageTitle();

    /** 获取左软键文案（如 "选项", "确定"），返回 null 或空串时隐藏 */
    CharSequence getSoftLeftText();

    /** 获取中软键文案（如 "选择", "打开"），返回 null 或空串时隐藏 */
    CharSequence getSoftCenterText();

    /** 获取右软键文案（如 "返回", "清除"），返回 null 或空串时隐藏 */
    CharSequence getSoftRightText();
}
