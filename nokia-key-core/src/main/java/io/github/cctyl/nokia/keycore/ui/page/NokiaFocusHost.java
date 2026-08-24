package io.github.cctyl.nokia.keycore.ui.page;

import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;

/**
 * 焦点事件接收接口。
 * <p>
 * 页面（Fragment 或 View 控制器）实现此接口即可接收来自宿主 Activity 的物理按键导航事件。
 * 宿主 {@code NokiaBaseActivity.dispatchKeyEvent()} 会将按键解析后的动作精准分发给当前前台页面。
 */
public interface NokiaFocusHost {

    /**
     * 方向键导航。
     *
     * @param direction 为 {@link NokiaKeyAction#UP}、{@link NokiaKeyAction#DOWN}、
     *                  {@link NokiaKeyAction#LEFT}、{@link NokiaKeyAction#RIGHT} 之一
     * @return true 表示已处理并消费该事件，宿主无需继续处理
     */
    boolean onDirection(int direction);

    /**
     * 确定/选择键被按下（{@link NokiaKeyAction#SELECT}）。
     *
     * @return true 表示已处理
     */
    boolean onSelect();

    /**
     * 左软键被按下（{@link NokiaKeyAction#SOFT_LEFT}）。
     *
     * @return true 表示已处理
     */
    boolean onSoftLeft();

    /**
     * 右软键被按下（{@link NokiaKeyAction#SOFT_RIGHT}）。
     *
     * @return true 表示已处理
     */
    boolean onSoftRight();

    /**
     * 返回键被按下（{@link NokiaKeyAction#BACK}）。
     *
     * @return true 表示已处理
     */
    boolean onBack();
}
