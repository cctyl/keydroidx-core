package io.github.cctyl.nokia.common.ui.focus;

import io.github.cctyl.nokia.common.model.KeydroidxKeyAction;

/**
 * 焦点事件接收接口。
 * <p>
 * 页面（Fragment 或 View 控制器）实现此接口即可接收来自宿主 Activity 的物理按键导航事件。
 * 宿主在 {@code dispatchKeyEvent()} 中将按键动作解析后分发给当前前台页面。
 */
public interface KeydroidxFocusHost {

    /**
     * 方向键导航。
     *
     * @param direction 为 {@link KeydroidxKeyAction#UP}、{@link KeydroidxKeyAction#DOWN}、
     *                  {@link KeydroidxKeyAction#LEFT}、{@link KeydroidxKeyAction#RIGHT} 之一
     * @return true 表示已处理并消费该事件，宿主无需继续处理
     */
    boolean onDirection(int direction);

    /**
     * 确定/选择键被按下（{@link KeydroidxKeyAction#SELECT}）。
     *
     * @return true 表示已处理
     */
    boolean onSelect();

    /**
     * 左软键被按下（{@link KeydroidxKeyAction#SOFT_LEFT}）。
     *
     * @return true 表示已处理
     */
    boolean onSoftLeft();

    /**
     * 右软键被按下（{@link KeydroidxKeyAction#SOFT_RIGHT}）。
     *
     * @return true 表示已处理
     */
    boolean onSoftRight();

    /**
     * 返回键被按下（{@link KeydroidxKeyAction#BACK}）。
     *
     * @return true 表示已处理
     */
    boolean onBack();
}
