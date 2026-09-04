package io.github.cctyl.nokia.sample;

import android.app.Application;

import io.github.cctyl.nokia.common.feedback.NokiaFeedback;
import io.github.cctyl.nokia.common.feedback.NokiaFeedbackConfig;
import io.github.cctyl.nokia.common.feedback.NokiaInstall;
import io.github.cctyl.nokia.common.log.NokiaLog;

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
        NokiaLog.setTag(TAG);
        NokiaLog.init(this);
        NokiaLog.installCrashHandler(this);

        // 2. 初始化反馈 + 安装统计（共用同一份配置）
        //    installUrl 传 null 也会自动从 uploadUrl 推导（/upload -> /install）
        NokiaFeedback.init(new NokiaFeedbackConfig(
                BuildConfig.FEEDBACK_UPLOAD_URL,
                BuildConfig.FEEDBACK_INSTALL_URL,
                BuildConfig.FEEDBACK_SECRET_KEY,
                "keydroidx-sample",
                BuildConfig.VERSION_NAME,
                null));

        // 3. 首次安装 / 版本升级时自动上报一次设备信息
        //    后台执行、不阻塞、不抛异常；同版本不重复打
        NokiaInstall.reportOnce(this);

        NokiaLog.i(TAG, "SampleApplication initialized");
    }
}
