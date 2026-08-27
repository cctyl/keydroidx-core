package io.github.cctyl.nokia.keycore.ui;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.model.NokiaKeyAction;
import io.github.cctyl.nokia.common.ui.NokiaFontManager;
import io.github.cctyl.nokia.common.ui.NokiaTheme;
import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 诺基亚复古风格基类 Activity（独立 App SDK 接入层）。
 * <p>
 * 继承自 {@link io.github.cctyl.nokia.common.ui.NokiaBaseActivity} 纯骨架，
 * 自动绑定 {@link NokiaClient}：
 * <ul>
 *     <li>按键解析自动关联跨进程桌面 Provider / 本地配置 / 默认兜底三级降级</li>
 *     <li>自动监听桌面主题切换与字体热更新并刷新当前窗口</li>
 * </ul>
 */
public abstract class NokiaBaseActivity extends io.github.cctyl.nokia.common.ui.NokiaBaseActivity implements NokiaClient.OnConfigChangedListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册全局配置监听
        NokiaClient.get(this).addListener(this);

        // 主动触发一次主题与字体应用
        NokiaTheme.ThemeDef currentTheme = NokiaTheme.getTheme(NokiaClient.get(this).getCurrentThemeId());
        if (currentTheme != null) {
            onThemeChanged(NokiaClient.get(this).getCurrentThemeId(), currentTheme);
        }
        onFontChanged(NokiaClient.get(this).getCurrentFontId(), NokiaClient.get(this).getCurrentFontScale());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NokiaClient.get(this).removeListener(this);
    }

    /**
     * 按键解析：委托 {@link NokiaClient}（支持三级平滑降级）。
     */
    @Override
    public int resolveAction(@NonNull KeyEvent event) {
        if (event == null) return NokiaKeyAction.UNKNOWN;
        return NokiaClient.get(this).getKeyBinding().resolveAction(event.getKeyCode());
    }

    @Override
    public void onKeysChanged(@NonNull NokiaKeyBinding binding, @NonNull NokiaClient.ConfigSource source) {
        // 供子类按需重写
    }

    @Override
    public void onThemeChanged(@NonNull String themeId, @NonNull NokiaTheme.ThemeDef theme) {
        applyTheme(theme);
    }

    @Override
    public void onFontChanged(@NonNull String fontId, float fontScale) {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            NokiaFontManager.applyToViewTree(getWindow().getDecorView());
        } else if (rootContainer != null) {
            NokiaFontManager.applyToViewTree(rootContainer);
        }
    }
}
