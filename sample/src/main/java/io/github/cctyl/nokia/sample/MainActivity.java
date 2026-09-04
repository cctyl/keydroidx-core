package io.github.cctyl.nokia.sample;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.common.feedback.NokiaFeedback;
import io.github.cctyl.nokia.common.feedback.NokiaFeedbackConfig;
import io.github.cctyl.nokia.common.feedback.NokiaInstall;
import io.github.cctyl.nokia.common.log.NokiaLog;
import io.github.cctyl.nokia.common.update.NokiaUpdateConfig;
import io.github.cctyl.nokia.common.update.NokiaUpdateDialog;
import io.github.cctyl.nokia.common.update.NokiaUpdateResult;
import io.github.cctyl.nokia.common.update.NokiaUpdateChecker;
import io.github.cctyl.nokia.shizuku.MiniShizuku;

/**
 * 示例应用：mini_shizuku 调用测试 + 安装统计上报测试 + 检查更新测试。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SampleActivity";
    /** 测试用：被检查更新的 GitHub 仓库 */
    private static final String TEST_REPO_URL = "https://github.com/cctyl/keydroidx-launcher";

    private Button btnTestShizuku;
    private TextView tvShizukuResult;
    private Button btnInstallReport;
    private Button btnInstallReset;
    private TextView tvInstallResult;
    private Button btnTestPermission;
    private TextView tvPermissionResult;
    private Button btnCheckUpdateDialog;
    private Button btnCheckUpdateApi;
    private TextView tvUpdateResult;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_main);

        btnTestShizuku = findViewById(R.id.btnTestShizuku);
        tvShizukuResult = findViewById(R.id.tvShizukuResult);
        btnInstallReport = findViewById(R.id.btnInstallReport);
        btnInstallReset = findViewById(R.id.btnInstallReset);
        tvInstallResult = findViewById(R.id.tvInstallResult);
        btnTestPermission = findViewById(R.id.btnTestPermission);
        tvPermissionResult = findViewById(R.id.tvPermissionResult);
        btnCheckUpdateDialog = findViewById(R.id.btnCheckUpdateDialog);
        btnCheckUpdateApi = findViewById(R.id.btnCheckUpdateApi);
        tvUpdateResult = findViewById(R.id.tvUpdateResult);

        // 检查更新测试（复古弹窗一站式：检查 -> 弹窗 -> 跳转）
        btnCheckUpdateDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvUpdateResult.setText("检查更新中（弹窗模式），详见 logcat（tag: NokiaUpdateChecker）...");
                NokiaUpdateDialog.checkAndShow(MainActivity.this, buildUpdateConfig());
            }
        });

        // 检查更新测试（纯回调 API：宿主自建 UI 时用这个）
        btnCheckUpdateApi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvUpdateResult.setText("检查更新中（回调模式）...");
                NokiaUpdateChecker.check(MainActivity.this, buildUpdateConfig(),
                        new NokiaUpdateChecker.Callback() {
                            @Override
                            public void onResult(NokiaUpdateResult result) {
                                tvUpdateResult.setText(result.toString());
                            }
                        });
            }
        });

        // 权限测试
        btnTestPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvPermissionResult.setText("正在通过 NokiaPermissionManager 发起权限申请...");
                java.util.List<String> perms = io.github.cctyl.nokia.common.permission.NokiaPermissionManager.getRequiredAppListPermissions(MainActivity.this);
                perms.add(com.hjq.permissions.Permission.READ_PHONE_STATE);

                io.github.cctyl.nokia.common.permission.NokiaPermissionManager.requestWithNokiaDialog(
                        MainActivity.this,
                        "权限申请",
                        "需要获取应用列表与手机状态权限以测试生态能力",
                        perms,
                        new com.hjq.permissions.OnPermissionCallback() {
                            @Override
                            public void onGranted(java.util.List<String> permissions, boolean allGranted) {
                                boolean hasAppList = io.github.cctyl.nokia.common.permission.NokiaPermissionManager.hasAppListPermission(MainActivity.this);
                                tvPermissionResult.setText("授权完成！allGranted=" + allGranted
                                        + "\n已授权项: " + permissions
                                        + "\nhasAppListPermission: " + hasAppList);
                                Toast.makeText(MainActivity.this, "权限授权成功！", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onDenied(java.util.List<String> permissions, boolean doNotAskAgain) {
                                tvPermissionResult.setText("权限被拒绝！doNotAskAgain=" + doNotAskAgain
                                        + "\n被拒绝项: " + permissions);
                                Toast.makeText(MainActivity.this, "权限被拒绝", Toast.LENGTH_SHORT).show();
                                if (doNotAskAgain) {
                                    io.github.cctyl.nokia.common.permission.NokiaPermissionManager.showSettingDialog(
                                            MainActivity.this,
                                            "权限受限",
                                            "权限被永久拒绝，请前往系统设置手动开启应用列表或读取状态权限。",
                                            null
                                    );
                                }
                            }
                        }
                );
            }
        });

        // mini_shizuku 测试
        MiniShizuku.init(this);

        btnTestShizuku.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvShizukuResult.setText("正在执行命令...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean isOnline = MiniShizuku.isRunning();
                        if (!isOnline) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvShizukuResult.setText("mini_shizuku 离线！");
                                    Toast.makeText(MainActivity.this, "mini_shizuku 离线", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        final String output = MiniShizuku.execWithOutput("id; whoami");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (output != null) {
                                    tvShizukuResult.setText("执行成功：\n" + output);
                                    Toast.makeText(MainActivity.this, "执行成功！", Toast.LENGTH_SHORT).show();
                                } else {
                                    tvShizukuResult.setText("执行失败或鉴权未通过！");
                                    Toast.makeText(MainActivity.this, "执行失败！", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }).start();
            }
        });

        // 安装统计：幂等触发（同版本会跳过）
        btnInstallReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInstallResult.setText("触发安装上报（幂等，同版本会跳过）...");
                NokiaInstall.reportOnce(MainActivity.this);
                // reportOnce 是异步的，给个提示即可
                tvInstallResult.append("\n已触发，详见 logcat（tag: NokiaInstall / InstallUploader）");
            }
        });

        // 清除已上报记录并重新上报（便于反复测试）
        btnInstallReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInstallResult.setText("已清除本地记录，重新上报中...");
                clearInstallRecord(MainActivity.this);
                NokiaInstall.reportOnce(MainActivity.this);
                tvInstallResult.append("\n详见 logcat（tag: NokiaInstall / InstallUploader）");
            }
        });

        // 显示当前配置
        NokiaFeedbackConfig cfg = NokiaFeedback.getConfig();
        if (cfg != null) {
            tvInstallResult.setText("配置：app=" + cfg.appName
                    + " ver=" + cfg.appVersion
                    + "\ninstallUrl=" + cfg.resolveInstallUrl());
        }
    }

    /** 构建检查更新配置（仓库地址由调用者传入，此处用测试仓库） */
    private static NokiaUpdateConfig buildUpdateConfig() {
        return new NokiaUpdateConfig(TEST_REPO_URL);
    }

    /** 清除安装上报的本地幂等记录，使下次 reportOnce 强制重新上报 */
    private static void clearInstallRecord(Context context) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences("nokia_install_report", Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (Throwable t) {
            NokiaLog.w(TAG, "clearInstallRecord error: " + t.getMessage());
        }
    }
}
