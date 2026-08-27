package io.github.cctyl.nokia.common.model;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * 按键解析器接口：将物理 {@link KeyEvent} 翻译为 {@link NokiaKeyAction} 语义动作。
 *
 * <p>用于在 {@code nokia-common} 层打破对 {@code NokiaKeyClient} 的依赖。
 * 不同宿主各自实现：</p>
 * <ul>
 *   <li>独立 App（经 {@code nokia-key-core}）：{@code NokiaBaseActivity} 实现此接口，
 *       委托 {@code NokiaKeyClient.getBinding().resolveAction(event)}（跨进程/本地配置/兜底三级降级）。</li>
 *   <li>原键桌面 Launcher：桌面 Activity 实现此接口，委托本地 {@code NokiaKeyBinding}。</li>
 * </ul>
 *
 * <p>弹窗等通用 UI 组件通过宿主 Context 获取解析器；若宿主未实现，回退到
 * {@link DefaultKeyResolver}（标准 Android 键码映射）。</p>
 */
public interface KeyResolver {

    /**
     * 解析按键事件为语义动作。
     *
     * @param event 按键事件
     * @return {@link NokiaKeyAction} 动作 ID；未识别返回 {@link NokiaKeyAction#UNKNOWN}
     */
    int resolveAction(@NonNull KeyEvent event);
}
