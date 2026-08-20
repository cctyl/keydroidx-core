package io.github.cctyl.nokia.sample;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.keycore.NokiaClient;
import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;
import io.github.cctyl.nokia.keycore.ui.NokiaKeyWizardActivity;
import io.github.cctyl.nokia.keycore.ui.NokiaTheme;

/**
 * 示例应用主页面
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvKeyInfo;
    private TextView btnWizard;
    private TextView btnTestSelect;
    private TextView btnExit;

    private NokiaKeyBinding keyBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvKeyInfo = findViewById(R.id.tvKeyInfo);
        btnWizard = findViewById(R.id.btnWizard);
        btnTestSelect = findViewById(R.id.btnTestSelect);
        btnExit = findViewById(R.id.btnExit);

        btnWizard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NokiaKeyWizardActivity.start(MainActivity.this);
            }
        });

        btnTestSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "点击了【测试确认】按钮", Toast.LENGTH_SHORT).show();
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 注册按键变化监听
        NokiaClient.get(this).registerListener(new NokiaClient.OnConfigChangedListener() {
            @Override
            public void onKeysChanged(NokiaKeyBinding newBinding, NokiaClient.ConfigSource source) {
                keyBinding = newBinding;
                updateDisplay();
            }

            @Override
            public void onThemeChanged(String themeId, NokiaTheme.ThemeDef theme) {
            }

            @Override
            public void onFontChanged(String fontId, float fontScale) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台刷新按键配置
        keyBinding = NokiaKeyClient.get(this).getBinding();
        updateDisplay();
    }

    private void updateDisplay() {
        if (keyBinding == null) return;

        boolean isDesktop = NokiaClient.get(this).getConfigSource() == NokiaClient.ConfigSource.DESKTOP_RELEASE 
                || NokiaClient.get(this).getConfigSource() == NokiaClient.ConfigSource.DESKTOP_DEBUG;
        if (isDesktop) {
            tvStatus.setText("✓ 已成功从 KeydroidX 原键桌面同步按键");
            tvStatus.setTextColor(0xFF0055AA);
        } else {
            tvStatus.setText("! 未连接桌面 (使用本地/默认按键)");
            tvStatus.setTextColor(0xFF666666);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NokiaKeyBinding.ACTION_COUNT; i++) {
            String name = NokiaKeyAction.getActionName(i);
            int code = keyBinding.getKeyCode(i);
            sb.append(name).append(": KeyCode ").append(code).append("\n");
        }
        tvKeyInfo.setText(sb.toString());
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int keyCode = event.getKeyCode();
            int action = keyBinding != null ? keyBinding.resolveAction(keyCode) : NokiaKeyAction.ACTION_UNKNOWN;
            android.util.Log.i("SampleMain", "【物理按键按下】keyCode=" + keyCode + " -> 映射动作: " + NokiaKeyAction.getActionName(action));

            if (action == NokiaKeyAction.ACTION_SOFT_LEFT) {
                btnWizard.performClick();
                return true;
            } else if (action == NokiaKeyAction.ACTION_SELECT) {
                btnTestSelect.performClick();
                return true;
            } else if (action == NokiaKeyAction.ACTION_SOFT_RIGHT) {
                btnExit.performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
