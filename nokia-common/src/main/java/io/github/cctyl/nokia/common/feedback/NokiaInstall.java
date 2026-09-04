package io.github.cctyl.nokia.common.feedback;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.cctyl.nokia.common.log.NokiaLog;

/**
 * 安装统计上报门面（单例/静态方法）。
 *
 * <p>与 {@link NokiaFeedback} 共享 {@link NokiaFeedbackConfig}（同一台服务器、同一套
 * HMAC-SHA256 鉴权、同一个应用标识与版本），仅接口路径不同（{@code /install}）。</p>
 *
 * <h3>上报时机（自动幂等）</h3>
 * <ul>
 *   <li>首次安装启动时上报一次；</li>
 *   <li>APP 版本升级后（versionName 变化）再上报一次；</li>
 *   <li>其余情况（同一 android_id + 同一版本）直接跳过，不重复上报。</li>
 * </ul>
 *
 * <p>服务端按 {@code (app, android_id)} 去重：首次计为一次安装；同一设备再次上报
 * （例如升级）不重复计安装数，但会把该设备的版本等字段更新为最新。</p>
 *
 * <h3>接入示例</h3>
 * <pre>{@code
 * // Application.onCreate 中，紧跟 NokiaFeedback.init(...) 之后调用
 * NokiaInstall.reportOnce(this);
 * }</pre>
 * 失败静默返回，绝不影响 APP 正常使用；失败时不记录「已上报」，下次启动还会再试。
 *
 * <p>协议规范见 log_upload/docs/CLIENT_API.md 第 3 节。</p>
 */
public final class NokiaInstall {

    private static final String TAG = "NokiaInstall";
    private static final String PREFS = "nokia_install_report";
    private static final String KEY_LAST_ANDROID_ID = "last_android_id";
    private static final String KEY_LAST_VERSION = "last_version";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private NokiaInstall() {
    }

    /**
     * 需要时才上报：已记录过相同 (android_id, 版本) 就直接返回。
     *
     * <p>调用时机：建议在 {@code Application.onCreate} 里调用，内部在后台单线程池执行，
     * 不阻塞主线程、不抛异常、不等结果。</p>
     *
     * <p>未调用 {@link NokiaFeedback#init(NokiaFeedbackConfig)} 或配置无效时静默跳过，
     * 不会影响反馈上报能力。</p>
     *
     * @param context 任意 Context，内部取 ApplicationContext
     */
    public static void reportOnce(Context context) {
        if (context == null) {
            return;
        }
        final NokiaFeedbackConfig cfg = NokiaFeedback.getConfig();
        if (cfg == null || !cfg.isValid()) {
            NokiaLog.w(TAG, "reportOnce aborted: NokiaFeedbackConfig not initialized or invalid");
            return;
        }
        final String installUrl = cfg.resolveInstallUrl();
        if (installUrl == null || installUrl.isEmpty()) {
            NokiaLog.w(TAG, "reportOnce aborted: install url not resolved");
            return;
        }
        // 注意：不在主线程同步取 applicationContext —— 调用方可能在 Application.attachBaseContext
        // 阶段就调用本方法，此时 Application 尚在 attach 过程，getApplicationContext() 会返回 null。
        // 把原 context 传进后台线程，执行时 attachBaseContext 已返回，再取 applicationContext 才安全。
        final Context callerContext = context;
        sExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Context app = callerContext.getApplicationContext();
                if (app == null) {
                    app = callerContext;
                }
                doReportOnce(app, cfg, installUrl);
            }
        });
    }

    private static void doReportOnce(Context context, NokiaFeedbackConfig cfg, String installUrl) {
        try {
            String androidId = readAndroidId(context);
            if (androidId == null) {
                // 取不到 / 全零 → 放弃本次上报，避免产生脏数据被服务端拒
                NokiaLog.w(TAG, "reportOnce aborted: android_id unavailable");
                return;
            }

            String lastId = prefs(context).getString(KEY_LAST_ANDROID_ID, null);
            String lastVer = prefs(context).getString(KEY_LAST_VERSION, null);
            // 同一台设备、同一版本已上报过 → 跳过
            if (androidId.equals(lastId) && cfg.appVersion != null && cfg.appVersion.equals(lastVer)) {
                return;
            }

            // 复用设备信息采集器（仅公开 API，不含隐私敏感数据）
            Map<String, Object> extras = DeviceInfoCollector.collect(context);

            String json = InstallUploader.buildInstallJson(
                    cfg.appName, cfg.appVersion, androidId, extras);
            boolean ok = InstallUploader.send(installUrl, cfg.secretKeyHex, json);
            if (ok) {
                // 只有上报成功才记录；失败的下次启动还会再试
                prefs(context).edit()
                        .putString(KEY_LAST_ANDROID_ID, androidId)
                        .putString(KEY_LAST_VERSION, cfg.appVersion)
                        .apply();
                NokiaLog.d(TAG, "install report sent: " + cfg.appName + " v" + cfg.appVersion);
            } else {
                NokiaLog.w(TAG, "install report failed, will retry next launch");
            }
        } catch (Throwable t) {
            // 全程兜底：统计失败绝不能影响 APP 正常使用
            NokiaLog.w(TAG, "reportOnce error: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage(), t);
        }
    }

    /**
     * 读取设备 ANDROID_ID。
     *
     * <p>Android 8+ 的 ANDROID_ID 按 (签名 key, 用户, 设备) 作用域隔离，
     * 不同签名的 APP 拿到的值不一样——服务端也按 (app, android_id) 去重，与此一致。</p>
     *
     * @return 16 位十六进制串；取不到、空白、全零时返回 null（此时放弃上报）
     */
    @android.annotation.SuppressLint("HardwareIds")
    private static String readAndroidId(Context context) {
        String id = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        // 全零是部分 ROM 的返回值，无法用于去重，服务端也会拒绝
        boolean allZero = true;
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) != '0') {
                allZero = false;
                break;
            }
        }
        return allZero ? null : id;
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
