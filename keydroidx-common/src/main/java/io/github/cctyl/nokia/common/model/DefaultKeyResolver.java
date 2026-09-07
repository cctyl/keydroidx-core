package io.github.cctyl.nokia.common.model;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * 标准按键解析器。
 *
 * <p>使用 Android 标准 DPAD / MENU / BACK / ENDCALL / CALL 键码映射到
 * {@link KeydroidxKeyAction} 语义，作为 {@link KeyResolver} 的兜底实现。
 * 当通用 UI 组件所在的宿主（Activity）未实现 {@link KeyResolver} 时使用此实现，
 * 保证弹窗在任何环境下方向键与软键均可用。</p>
 *
 * <p>该映射与 {@code keydroidx-key-core} 的 {@code KeydroidxKeyBinding.initDefaults()} 完全一致，
 * 生态内统一。</p>
 */
public final class DefaultKeyResolver implements KeyResolver {

    public static final DefaultKeyResolver INSTANCE = new DefaultKeyResolver();

    private DefaultKeyResolver() {}

    @Override
    public int resolveAction(@NonNull KeyEvent event) {
        if (event == null) return KeydroidxKeyAction.UNKNOWN;
        int kc = event.getKeyCode();
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return KeydroidxKeyAction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return KeydroidxKeyAction.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return KeydroidxKeyAction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return KeydroidxKeyAction.RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return KeydroidxKeyAction.SELECT;
            case KeyEvent.KEYCODE_MENU:
                return KeydroidxKeyAction.SOFT_LEFT;
            case KeyEvent.KEYCODE_BACK:
                return KeydroidxKeyAction.SOFT_RIGHT;
            case KeyEvent.KEYCODE_ENDCALL:
                return KeydroidxKeyAction.LOCK_SCREEN;
            case KeyEvent.KEYCODE_CALL:
                return KeydroidxKeyAction.CALL;
            default:
                return KeydroidxKeyAction.UNKNOWN;
        }
    }
}
