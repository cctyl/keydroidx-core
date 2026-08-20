package io.github.cctyl.nokia.keycore.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.keycore.NokiaKeyClient;
import io.github.cctyl.nokia.keycore.R;
import io.github.cctyl.nokia.keycore.model.NokiaKeyAction;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 独立的诺基亚物理按键配置向导 Activity
 */
public class NokiaKeyWizardActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, NokiaKeyWizardActivity.class);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static final int[] STEPS = {
            NokiaKeyAction.ACTION_UP,
            NokiaKeyAction.ACTION_DOWN,
            NokiaKeyAction.ACTION_LEFT,
            NokiaKeyAction.ACTION_RIGHT,
            NokiaKeyAction.ACTION_SELECT,
            NokiaKeyAction.ACTION_SOFT_LEFT,
            NokiaKeyAction.ACTION_SOFT_RIGHT,
            NokiaKeyAction.ACTION_LOCK_SCREEN,
            NokiaKeyAction.ACTION_CALL
    };

    private static final String[] STEP_NAMES = {
            "【上】键 (UP)",
            "【下】键 (DOWN)",
            "【左】键 (LEFT)",
            "【右】键 (RIGHT)",
            "【确定】键 (SELECT/OK)",
            "【左软键】 (SOFT_LEFT)",
            "【右软键】 (SOFT_RIGHT)",
            "【锁屏/挂机】键 (LOCK)",
            "【拨号/通话】键 (CALL)"
    };

    private TextView tvStepIndicator;
    private TextView tvKeyPrompt;
    private TextView tvCapturedInfo;
    private Button btnSkip;
    private TextView btnCancel;

    private int currentStepIndex = 0;
    private NokiaKeyBinding draftBinding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isWaitingNext = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nokia_key_wizard);

        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        tvKeyPrompt = findViewById(R.id.tvKeyPrompt);
        tvCapturedInfo = findViewById(R.id.tvCapturedInfo);
        btnSkip = findViewById(R.id.btnSkip);
        btnCancel = findViewById(R.id.btnCancel);

        draftBinding = NokiaKeyClient.get(this).getBinding().clone();

        btnSkip.setOnClickListener(v -> nextStep());
        btnCancel.setOnClickListener(v -> finish());

        updateUi();
    }

    private void updateUi() {
        if (currentStepIndex >= STEPS.length) {
            finishWizard();
            return;
        }

        tvStepIndicator.setText(String.format("步骤 %d / %d", currentStepIndex + 1, STEPS.length));
        tvKeyPrompt.setText("请按下 " + STEP_NAMES[currentStepIndex]);
        tvCapturedInfo.setText("等待按键按下...");
        tvCapturedInfo.setBackgroundColor(0xFFEAF2FC);
        tvCapturedInfo.setTextColor(0xFF0055AA);
        isWaitingNext = false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isWaitingNext) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK && currentStepIndex == 0) {
                // 允许第一步按返回键退出向导
                return super.dispatchKeyEvent(event);
            }

            int currentAction = STEPS[currentStepIndex];
            draftBinding.bind(currentAction, keyCode);

            tvCapturedInfo.setText(String.format("已捕获: KeyCode %d (%s)", keyCode, KeyEvent.keyCodeToString(keyCode)));
            tvCapturedInfo.setBackgroundColor(0xFFE8F5E9);
            tvCapturedInfo.setTextColor(0xFF2E7D32);

            isWaitingNext = true;
            handler.postDelayed(this::nextStep, 600);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void nextStep() {
        currentStepIndex++;
        updateUi();
    }

    private void finishWizard() {
        draftBinding.save(this);
        NokiaKeyClient.get(this).reload();
        Toast.makeText(this, "按键向导配置已保存！", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
