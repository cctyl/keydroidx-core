package io.github.cctyl.nokia.sample;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.cctyl.nokia.shizuku.MiniShizuku;

/**
 * 示例应用：mini_shizuku 调用测试
 */
public class MainActivity extends AppCompatActivity {

    private Button btnTestShizuku;
    private TextView tvShizukuResult;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_main);

        btnTestShizuku = findViewById(R.id.btnTestShizuku);
        tvShizukuResult = findViewById(R.id.tvShizukuResult);

        // 初始化 mini_shizuku SDK（注入 Context）
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

                        // 执行测试命令
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
    }
}
