package io.github.cctyl.nokia.keycore.ui;

import io.github.cctyl.nokia.common.ui.KeydroidxFeedbackFragment;

/**
 * 诺基亚风格通用反馈页 Activity（超薄托管壳）。
 *
 * <p>独立 App 通过 {@code startActivity(new Intent(this, KeydroidxFeedbackActivity.class))} 启动；
 * 实际 UI 与业务逻辑由 {@code keydroidx-common} 的 {@link KeydroidxFeedbackFragment} 提供，
 * 桌面 Launcher 可直接使用 {@link KeydroidxFeedbackFragment} 压入 Fragment 栈，
 * 两端共享同一份 UI 源码，零重复维护。</p>
 *
 * <h3>按键与主题</h3>
 * <ul>
 *   <li>按键解析与主题复用本 Activity（实现 {@code KeyResolver} / {@code ThemeProvider}），
 *       自动分发给托管的 Fragment；</li>
 *   <li>文本输入复用 {@code keydroidx-common} 的 {@link io.github.cctyl.nokia.common.ui.KeydroidxTextInputFragment}
 *       （全屏编辑页，压入返回栈）。</li>
 * </ul>
 */
public class KeydroidxFeedbackActivity extends KeydroidxBaseActivity {

    @Override
    protected int getContentLayoutRes() {
        // 内容由 KeydroidxFeedbackFragment 提供，无需静态布局
        return 0;
    }

    @Override
    protected void onInitViews() {
        if (getSupportFragmentManager().findFragmentById(io.github.cctyl.nokia.common.R.id.midPanel) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(io.github.cctyl.nokia.common.R.id.midPanel, new KeydroidxFeedbackFragment())
                    .commitNow();
        }
        refreshPageBar();
    }
}
