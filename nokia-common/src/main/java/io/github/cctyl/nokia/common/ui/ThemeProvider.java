package io.github.cctyl.nokia.common.ui;

import android.content.Context;

/**
 * 主题提供者接口。
 * <p>
 * 用于在 {@code nokia-common} 层打破与 {@code NokiaClient} 的循环依赖：
 * <ul>
 *   <li>独立 App（集成 {@code nokia-key-core}）：{@code NokiaClient} 实现此接口，跨进程查询桌面 Provider 并缓存。</li>
 *   <li>原键桌面（直接依赖 {@code nokia-common}）：桌面自己实现此接口，直接从本地 SharedPreferences 读取主题。</li>
 * </ul>
 */
public interface ThemeProvider {

    /**
     * 获取当前生效的主题定义。
     *
     * @param context 上下文
     * @return 当前主题定义，未配置时应返回默认主题（如 Classic Blue）
     */
    NokiaTheme.ThemeDef getCurrentTheme(Context context);
}
