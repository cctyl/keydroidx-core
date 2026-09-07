package io.github.cctyl.nokia.keycore.ui;

import android.content.Intent;

import io.github.cctyl.nokia.common.ui.KeydroidxTextInputFragment;

/**
 * 全屏复古文本输入页 Activity（超薄托管壳）。
 *
 * <p>独立 App 通过 {@code startActivityForResult} 启动本页取一段文本；
 * 实际 UI 与业务逻辑由 {@code keydroidx-common} 的 {@link KeydroidxTextInputFragment} 提供，
 * 桌面 Launcher 直接把同一个 Fragment 压入自己的 Fragment 栈，两端共享同一份源码。</p>
 *
 * <p>用法：</p>
 * <pre>
 * Intent it = new Intent(this, KeydroidxTextInputActivity.class)
 *         .putExtra(KeydroidxTextInputActivity.EXTRA_TITLE, "问题描述")
 *         .putExtra(KeydroidxTextInputActivity.EXTRA_HINT, "描述问题与复现步骤")
 *         .putExtra(KeydroidxTextInputActivity.EXTRA_TEXT, currentText)
 *         .putExtra(KeydroidxTextInputActivity.EXTRA_MAX_CHARS, 500)
 *         .putExtra(KeydroidxTextInputActivity.EXTRA_MULTILINE, true);
 * startActivityForResult(it, REQUEST_CODE);
 * </pre>
 * 结果通过 {@link #RESULT_TEXT} 返回；取消时 resultCode 为 {@code RESULT_CANCELED}。
 *
 * <h3>按键与主题</h3>
 * <ul>
 *   <li>按键解析与主题复用本 Activity（实现 {@code KeyResolver} / {@code ThemeProvider}），
 *       自动分发给托管的 Fragment；</li>
 *   <li>软键与选项菜单语义见 {@link KeydroidxTextInputFragment}：LSK=选项菜单、
 *       CSK=确定、RSK=有内容时退格清除 / 空内容时返回、BACK=放弃退出。</li>
 * </ul>
 */
public class KeydroidxTextInputActivity extends KeydroidxBaseActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_HINT = "hint";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MAX_CHARS = "maxChars";
    /** 是否多行输入。历史版本固定为多行页，故默认 true。 */
    public static final String EXTRA_MULTILINE = "multiline";

    public static final String RESULT_TEXT = "resultText";

    private boolean confirmed = false;

    @Override
    protected int getContentLayoutRes() {
        // 内容由 KeydroidxTextInputFragment 提供，无需静态布局
        return 0;
    }

    @Override
    protected void onInitViews() {
        if (getSupportFragmentManager().findFragmentById(io.github.cctyl.nokia.common.R.id.midPanel) == null) {
            Intent it = getIntent();
            String title = it.getStringExtra(EXTRA_TITLE);
            String hint = it.getStringExtra(EXTRA_HINT);
            String text = it.getStringExtra(EXTRA_TEXT);
            int maxChars = it.getIntExtra(EXTRA_MAX_CHARS, 500);
            boolean multiline = it.getBooleanExtra(EXTRA_MULTILINE, true);

            KeydroidxTextInputFragment page = KeydroidxTextInputFragment.newInstance(
                    title == null ? "输入" : title, text, hint, multiline, maxChars);
            page.setOnConfirmListener(result -> {
                confirmed = true;
                setResult(RESULT_OK, new Intent().putExtra(RESULT_TEXT, result));
                finish();
            });

            getSupportFragmentManager().beginTransaction()
                    .replace(io.github.cctyl.nokia.common.R.id.midPanel, page)
                    .commitNow();
        }
        refreshPageBar();
    }

    @Override
    public void finish() {
        if (!confirmed && !isFinishing()) {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }
}
