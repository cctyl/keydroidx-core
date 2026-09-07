package io.github.cctyl.nokia.keycore.ui;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.cctyl.nokia.common.model.KeydroidxKeyAction;
import io.github.cctyl.nokia.common.ui.KeydroidxFontManager;
import io.github.cctyl.nokia.common.ui.KeydroidxTheme;
import io.github.cctyl.nokia.keycore.KeydroidxClient;
import io.github.cctyl.nokia.keycore.model.KeydroidxKeyBinding;

/**
 * 诺基亚复古风格基类 Activity（独立 App SDK 接入层）。
 * <p>
 * 继承自 {@link io.github.cctyl.nokia.common.ui.KeydroidxBaseActivity} 纯骨架，
 * 自动绑定 {@link KeydroidxClient}：
 * <ul>
 *     <li>按键解析自动关联跨进程桌面 Provider（Release/Debug）/ 本地配置 / 默认兜底四级平滑降级</li>
 *     <li>自动监听桌面主题切换与字体热更新并刷新当前窗口</li>
 * </ul>
 */
public abstract class KeydroidxBaseActivity extends io.github.cctyl.nokia.common.ui.KeydroidxBaseActivity implements KeydroidxClient.OnConfigChangedListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 先从 KeydroidxClient 同步一次字体与缩放状态到 KeydroidxFontManager，确保 super.onCreate 内部 inflate 视图时处于正确倍率
        KeydroidxClient client = KeydroidxClient.get(this);
        KeydroidxFontManager.setFontScale(client.getCurrentFontScale());
        KeydroidxFontManager.setCurrentFontId(client.getCurrentFontId());

        super.onCreate(savedInstanceState);

        // 注册全局配置监听
        client.addListener(this);

        // 主动触发一次主题与字体应用
        KeydroidxTheme.ThemeDef currentTheme = KeydroidxTheme.getTheme(client.getCurrentThemeId());
        if (currentTheme != null) {
            onThemeChanged(client.getCurrentThemeId(), currentTheme);
        }
        onFontChanged(client.getCurrentFontId(), client.getCurrentFontScale());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        KeydroidxClient.get(this).removeListener(this);
    }

    /**
     * 按键解析：委托 {@link KeydroidxClient}（支持四级平滑降级）。
     */
    @Override
    public int resolveAction(@NonNull KeyEvent event) {
        if (event == null) return KeydroidxKeyAction.UNKNOWN;
        return KeydroidxClient.get(this).getKeyBinding().resolveAction(event.getKeyCode());
    }

    @Override
    public void onKeysChanged(@NonNull KeydroidxKeyBinding binding, @NonNull KeydroidxClient.ConfigSource source) {
        // 供子类按需重写
    }

    @Override
    public void onThemeChanged(@NonNull String themeId, @NonNull KeydroidxTheme.ThemeDef theme) {
        applyTheme(theme);
    }

    @Override
    public void onFontChanged(@NonNull String fontId, float fontScale) {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            KeydroidxFontManager.applyToViewTree(getWindow().getDecorView());
        } else if (rootContainer != null) {
            KeydroidxFontManager.applyToViewTree(rootContainer);
        }
    }
}
