package io.github.cctyl.nokia.keycore.feedback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Map;

/**
 * 反馈上报统一入口（门面类）。
 *
 * <h3>接入方式</h3>
 * <ol>
 *   <li>宿主 APP 启动时初始化一次（值来自宿主自己的 BuildConfig，密钥绝不入库）：</li>
 * </ol>
 * <pre>{@code
 * NokiaFeedback.init(new NokiaFeedbackConfig(
 *         BuildConfig.KDFB_SERVER_HOST,
 *         BuildConfig.KDFB_SERVER_PORT,
 *         BuildConfig.KDFB_PRIVATE_KEY,
 *         "myapp",                      // 与服务端登记一致
 *         BuildConfig.VERSION_NAME,
 *         null));                       // 日志目录，null = 默认 files/logs
 * }</pre>
 * <ol start="2">
 *   <li>入口由宿主自行决定（设置页某项点击），跳转 SDK 内置反馈页：</li>
 * </ol>
 * <pre>{@code
 * startActivity(new Intent(this, NokiaFeedbackActivity.class));
 * }</pre>
 * <p>或自定义 UI 时直接调用 {@link #submit(Context, String, String, String, boolean, Callback)}。</p>
 *
 * <h3>行为说明</h3>
 * <ul>
 *   <li>全部耗时操作在后台线程执行，结果回调切回主线程；</li>
 *   <li>失败不自动重试（服务端有限流，重试只会加剧失败），由调用方提示用户手动重试；</li>
 *   <li>服务端对任何校验失败均静默断连，因此无法区分失败原因，统一提示"提交失败"即可。</li>
 * </ul>
 */
public final class NokiaFeedback {

    private NokiaFeedback() {
    }

    /** 反馈提交结果回调（主线程） */
    public interface Callback {
        /**
         * @param success true = 服务端确认成功
         */
        void onResult(boolean success);
    }

    private static volatile NokiaFeedbackConfig sConfig;

    /** 宿主 APP 启动时调用一次。传入 null 可清除配置（主要用于测试）。 */
    public static void init(NokiaFeedbackConfig config) {
        sConfig = config;
    }

    public static NokiaFeedbackConfig getConfig() {
        return sConfig;
    }

    /**
     * 解析日志目录：配置覆盖优先，否则使用统一约定目录。
     */
    public static File resolveLogDir(Context context) {
        NokiaFeedbackConfig c = sConfig;
        if (c != null && c.logDir != null) {
            return c.logDir;
        }
        return new File(context.getExternalFilesDir(null), "logs");
    }

    /**
     * 使用全局配置提交反馈（异步）。
     *
     * @param contact   用户联系方式（必填）
     * @param comment   问题描述（必填）
     * @param extraInfo 附加字段（选填），将与默认设备信息合并
     * @param attachLog 是否附带日志
     * @return false 表示配置缺失/参数非法未发起提交（不会触发回调）
     */
    public static boolean submit(Context context, String contact, String comment,
                                 Map<String, Object> extraInfo, boolean attachLog,
                                 Callback callback) {
        NokiaFeedbackConfig c = sConfig;
        if (c == null || !c.isValid() || isBlank(contact)) {
            return false;
        }
        Map<String, Object> extras = DeviceInfoCollector.collectWithApp(context);
        if (extraInfo != null) {
            extras.putAll(extraInfo);
        }
        FeedbackRequest req = new FeedbackRequest(
                c.host, c.port, c.privateKeyHex,
                c.appName, c.appVersion,
                contact, comment,
                resolveLogDir(context), attachLog, extras);
        return submit(req, callback);
    }

    /**
     * 完整参数提交（异步）。日志打包、签名、网络发送均在后台线程执行。
     *
     * @return false 表示参数不合法未发起提交（不会触发回调）
     */
    public static boolean submit(final FeedbackRequest request, final Callback callback) {
        if (request == null || !request.isConfigValid()
                || isBlank(request.appName) || isBlank(request.contact)) {
            return false;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = doSubmit(request);
                if (callback != null) {
                    mainHandler().post(new ResultRunnable(callback, ok));
                }
            }
        }, "nokia-feedback").start();
        return true;
    }

    private static boolean doSubmit(FeedbackRequest req) {
        try {
            KdfbUploader.ZipResult zipRes = req.attachLog
                    ? KdfbUploader.zipLogs(req.logDir) : null;
            byte[] zip = zipRes != null ? zipRes.zipBytes : new byte[0];
            String meta = KdfbUploader.buildMetaJson(
                    req.appName, req.appVersion, req.contact, req.comment, req.extras);
            return KdfbUploader.submit(req.host, req.port, req.privateKeyHex, meta, zip);
        } catch (Throwable t) {
            android.util.Log.w("NokiaFeedback", "submit failed: " + t);
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    private static Handler sMainHandler;

    private static Handler mainHandler() {
        synchronized (NokiaFeedback.class) {
            if (sMainHandler == null) {
                sMainHandler = new Handler(Looper.getMainLooper());
            }
            return sMainHandler;
        }
    }

    private static final class ResultRunnable implements Runnable {
        private final Callback callback;
        private final boolean ok;

        ResultRunnable(Callback callback, boolean ok) {
            this.callback = callback;
            this.ok = ok;
        }

        @Override
        public void run() {
            callback.onResult(ok);
        }
    }
}
