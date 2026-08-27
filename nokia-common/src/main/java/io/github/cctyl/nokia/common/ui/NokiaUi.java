package io.github.cctyl.nokia.common.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.model.DefaultKeyResolver;
import io.github.cctyl.nokia.common.model.KeyResolver;

/**
 * 通用 UI 组件与宿主之间的桥接工具。
 *
 * <p>提供从宿主 {@link Context} 获取 {@link KeyResolver} 与 {@link ThemeProvider}
 * 能力的统一入口，并带降级兜底：</p>
 * <ul>
 *   <li>若宿主（如 {@code NokiaBaseActivity}）实现了相应接口，直接复用宿主实现
 *       （跨进程按键同步 / 主题缓存）；</li>
 *   <li>否则回退到 {@link DefaultKeyResolver} 与默认 Classic Blue 主题，
 *       保证弹窗在任何 Context 下均可用。</li>
 * </ul>
 */
public final class NokiaUi {

    private NokiaUi() {}

    /**
     * 从宿主 Context 获取按键解析器；宿主未实现 {@link KeyResolver} 时回退到默认。
     */
    @NonNull
    public static KeyResolver getKeyResolver(@Nullable Context context) {
        if (context instanceof KeyResolver) {
            return (KeyResolver) context;
        }
        return DefaultKeyResolver.INSTANCE;
    }

    /**
     * 从宿主 Context 获取当前主题；优先使用宿主已注册的全局 {@link ThemeProvider}
     * （由 {@link io.github.cctyl.nokia.common.ui.NokiaTheme#setThemeProvider} 注册），
     * 未注册或查询失败时回退到默认 Classic Blue 主题。
     */
    @NonNull
    public static NokiaTheme.ThemeDef getTheme(@Nullable Context context) {
        // 优先复用全局 ThemeProvider（独立 App 的 NokiaClient / 桌面的 LauncherThemeProvider）
        try {
            return NokiaTheme.getCurrentTheme(context);
        } catch (Exception ignored) {
            return NokiaTheme.getTheme(NokiaTheme.THEME_CLASSIC_BLUE);
        }
    }
}
