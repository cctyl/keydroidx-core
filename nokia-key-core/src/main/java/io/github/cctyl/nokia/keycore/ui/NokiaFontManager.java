package io.github.cctyl.nokia.keycore.ui;

/**
 * @deprecated 请直接使用 {@link io.github.cctyl.nokia.common.ui.NokiaFontManager}。
 * <p>此桥接类用于保持对旧包名 {@code io.github.cctyl.nokia.keycore.ui.NokiaFontManager}
 * 调用的向后兼容；所有静态方法与状态均继承自 {@code nokia-common} 的实现，
 * 桌面 Launcher 与独立 App 共享同一份字体逻辑。</p>
 */
@Deprecated
public class NokiaFontManager extends io.github.cctyl.nokia.common.ui.NokiaFontManager {
}
