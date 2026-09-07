package io.github.cctyl.nokia.sample;

import android.app.Application;

import io.github.cctyl.nokia.common.feedback.KeydroidxFeedback;
import io.github.cctyl.nokia.common.feedback.KeydroidxFeedbackConfig;
import io.github.cctyl.nokia.common.feedback.KeydroidxInstall;
import io.github.cctyl.nokia.common.log.KeydroidxLog;

/**
 * 示例 Application：演示反馈 + 安装统计的完整初始化链路。
 *
 * <p>真实接入只需在宿主 Application.onCreate 里照抄本文件的三个步骤即可。</p>
 */
public class SampleApplication extends Application {

    private static final String TAG = "SampleApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. 初始化日志器（自动读取详细日志开关，安装崩溃捕获）
        KeydroidxLog.setTag(TAG);
        KeydroidxLog.init(this);
        KeydroidxLog.installCrashHandler(this);

        // 2. 初始化反馈 + 安装统计（共用同一份配置）
        //    只传一个根地址 baseUrl，SDK 内部自动拼接 /upload、/install 路径
        KeydroidxFeedback.init(new KeydroidxFeedbackConfig(
                BuildConfig.FEEDBACK_URL,
                BuildConfig.FEEDBACK_SECRET_KEY,
                "keydroidx-sample",
                BuildConfig.VERSION_NAME,
                null));

        // 3. 首次安装 / 版本升级时自动上报一次设备信息
        //    后台执行、不阻塞、不抛异常；同版本不重复打
        KeydroidxInstall.reportOnce(this);

        KeydroidxLog.i(TAG, "SampleApplication initialized");
    }
}
