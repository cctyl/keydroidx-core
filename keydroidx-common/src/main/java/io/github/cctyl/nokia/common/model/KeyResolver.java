package io.github.cctyl.nokia.common.model;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * 按键解析器接口：将物理 {@link KeyEvent} 翻译为 {@link KeydroidxKeyAction} 语义动作。
 *
 * <p>用于在 {@code keydroidx-common} 层打破对 {@code KeydroidxClient}（key-core）的依赖。
 * 不同宿主各自实现：</p>
 * <ul>
 *   <li>独立 App（经 {@code keydroidx-key-core}）：{@code KeydroidxBaseActivity} 实现此接口，
 *       委托 {@code KeydroidxClient.getBinding().resolveAction(event)}（跨进程/本地配置/兜底四级平滑降级）。</li>
 *   <li>原键桌面 Launcher：桌面 Activity 实现此接口，委托本地 {@code KeydroidxKeyBinding}。</li>
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
     * @return {@link KeydroidxKeyAction} 动作 ID；未识别返回 {@link KeydroidxKeyAction#UNKNOWN}
     */
    int resolveAction(@NonNull KeyEvent event);
}
