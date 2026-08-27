package io.github.cctyl.nokia.common.model;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * 标准按键解析器。
 *
 * <p>使用 Android 标准 DPAD / MENU / BACK / ENDCALL / CALL 键码映射到
 * {@link NokiaKeyAction} 语义，作为 {@link KeyResolver} 的兜底实现。
 * 当通用 UI 组件所在的宿主（Activity）未实现 {@link KeyResolver} 时使用此实现，
 * 保证弹窗在任何环境下方向键与软键均可用。</p>
 *
 * <p>该映射与 {@code nokia-key-core} 的 {@code NokiaKeyBinding.initDefaults()} 完全一致，
 * 生态内统一。</p>
 */
public final class DefaultKeyResolver implements KeyResolver {

    public static final DefaultKeyResolver INSTANCE = new DefaultKeyResolver();

    private DefaultKeyResolver() {}

    @Override
    public int resolveAction(@NonNull KeyEvent event) {
        if (event == null) return NokiaKeyAction.UNKNOWN;
        int kc = event.getKeyCode();
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return NokiaKeyAction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return NokiaKeyAction.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return NokiaKeyAction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return NokiaKeyAction.RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return NokiaKeyAction.SELECT;
            case KeyEvent.KEYCODE_MENU:
                return NokiaKeyAction.SOFT_LEFT;
            case KeyEvent.KEYCODE_BACK:
                return NokiaKeyAction.SOFT_RIGHT;
            case KeyEvent.KEYCODE_ENDCALL:
                return NokiaKeyAction.LOCK_SCREEN;
            case KeyEvent.KEYCODE_CALL:
                return NokiaKeyAction.CALL;
            default:
                return NokiaKeyAction.UNKNOWN;
        }
    }
}
