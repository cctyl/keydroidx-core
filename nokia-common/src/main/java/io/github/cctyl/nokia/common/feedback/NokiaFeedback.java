package io.github.cctyl.nokia.common.feedback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.cctyl.nokia.common.log.NokiaLog;

/**
 * 反馈上报门面（单例/静态方法）。
 *
 * <p>宿主接入示例：</p>
 * <pre>
 *   NokiaFeedback.init(new NokiaFeedbackConfig(
 *       BuildConfig.FEEDBACK_UPLOAD_URL,
 *       BuildConfig.FEEDBACK_SECRET_KEY,
 *       "myapp",
 *       BuildConfig.VERSION_NAME,
 *       null)); // null = 使用默认日志目录 Android/data/&lt;包名&gt;/log
 * </pre>
 */
public class NokiaFeedback {

    private static final String TAG = "NokiaFeedback";
    private static volatile NokiaFeedbackConfig sCachedConfig;
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    protected NokiaFeedback() {
    }

    /**
     * 注册全局配置（APP 启动时调用一次）。
     */
    public static void init(NokiaFeedbackConfig config) {
        sCachedConfig = config;
    }

    /**
     * 获取已注册的配置，未注册返回 null。
     */
    public static NokiaFeedbackConfig getConfig() {
        return sCachedConfig;
    }

    /**
     * 检查当前是否已完成有效配置。
     */
    public static boolean isConfigured() {
        NokiaFeedbackConfig cfg = sCachedConfig;
        return cfg != null && cfg.isValid();
    }

    /**
     * 解析实际使用的日志目录。
     * 如果配置中指定了 logDir 则优先使用；否则使用默认目录 Android/data/<包名>/log
     */
    public static File resolveLogDir(Context context) {
        NokiaFeedbackConfig cfg = sCachedConfig;
        if (cfg != null && cfg.logDir != null) {
            return cfg.logDir;
        }
        return context != null ? NokiaLog.getDefaultLogDir(context) : null;
    }

    /**
     * 异步提交反馈。
     *
     * @param context   上下文，用于兜底解析默认日志目录及设备信息 extras
     * @param contact   联系方式（QQ / 微信 / 邮箱等，必填）
     * @param comment   问题描述（可选）
     * @param extraInfo 自定义额外信息（将与系统设备信息合并）
     * @param attachLog 是否附带运行日志
     * @param callback  主线程结果回调
     */
    public static void submit(Context context,
                              String contact,
                              String comment,
                              Map<String, Object> extraInfo,
                              boolean attachLog,
                              Callback callback) {
        NokiaFeedbackConfig cfg = sCachedConfig;
        if (cfg == null || !cfg.isValid()) {
            NokiaLog.w(TAG, "submit aborted: NokiaFeedbackConfig not initialized or invalid");
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }

        File logDir = resolveLogDir(context);

        Map<String, Object> extras = DeviceInfoCollector.collect(context);
        if (extraInfo != null) {
            extras.putAll(extraInfo);
        }

        FeedbackRequest req = new FeedbackRequest(
                cfg.uploadUrl,
                cfg.secretKeyHex,
                cfg.appName,
                cfg.appVersion,
                contact,
                comment,
                logDir,
                attachLog,
                extras);

        doSubmit(req, callback);
    }

    /**
     * 底层提交方法，在独立单线程池中执行并在主线程回调。
     */
    public static void doSubmit(final FeedbackRequest req, final Callback callback) {
        sExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final boolean success = FeedbackUploader.submit(
                        req.uploadUrl,
                        req.secretKeyHex,
                        req.appName,
                        req.appVersion,
                        req.contact,
                        req.comment,
                        req.attachLog ? req.logDir : null,
                        req.extras);

                if (callback != null) {
                    sMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(success);
                        }
                    });
                }
            }
        });
    }

    /**
     * 结果回调接口（保证在 Android 主线程派发）。
     */
    public interface Callback {
        void onResult(boolean success);
    }
}
