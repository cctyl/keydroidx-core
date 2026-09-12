package io.github.cctyl.nokia.common.feedback;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.cctyl.nokia.common.log.KeydroidxLog;
import io.github.cctyl.nokia.common.log.LogErrorMarker;

/**
 * 崩溃 / 错误日志「自动上报」门面。
 *
 * <p>解决「用户不点反馈，崩溃现场就永远拿不到」的问题：</p>
 * <ol>
 *   <li><b>本次运行</b>：进程内任意一处出现未捕获异常（Error / RuntimeException）
 *       或显式记录的 ERROR 级日志（{@link KeydroidxLog#e}）时，
 *       通过 {@link KeydroidxLog#setErrorMarker} 注册的回调<b>同步</b>落一个
 *       「待上传标记」文件 {@code <logDir>/pending_report.json}
 *       （含异常类型、消息、完整堆栈、进程/线程/时间）。</li>
 *   <li><b>下次启动</b>：调用 {@link #uploadPendingIfAny(Context)}，
 *       发现标记即调用 {@link KeydroidxFeedback#submit} 把运行日志（zip）自动上传。</li>
 *   <li><b>上传成功才重置标记</b>；失败保留标记，下次启动继续尝试，形成闭环。</li>
 * </ol>
 *
 * <p>标记文件本身也放在日志目录中，会随 zip 一起上传，因此即使崩溃堆栈来不及写入
 * 当日日志文件（崩溃时进程随时被杀），现场也不会丢。</p>
 *
 * <h3>节流（重要）</h3>
 * 服务端 {@code /upload} 按出口 IP 限流（3 次/分、20 次/天），超额会封禁 IP 1 小时。
 * 因此自动上报做了两道保护：两次尝试间隔 {@link #MIN_INTERVAL_MS}、
 * 每天最多 {@link #MAX_PER_DAY} 次；被节流时标记保留，择机再传。
 *
 * <h3>接入示例</h3>
 * <pre>{@code
 * // Application 中（主进程：注册标记 + 尝试上传上次遗留的报告）
 * KeydroidxFeedback.init(config);
 * KeydroidxCrashReporter.install(this);
 * KeydroidxCrashReporter.uploadPendingIfAny(this);
 *
 * // 子进程（如 :midlet）只需注册标记，由主进程负责上传
 * KeydroidxCrashReporter.install(this);
 * }</pre>
 *
 * <p>用户隐私：自动上报是静默的，可通过 {@link #setAutoUploadEnabled} 关闭
 * （默认开启）。日志内容可能含用户数据，接入方需自行评估并在隐私政策中说明。</p>
 */
public final class KeydroidxCrashReporter {

    private static final String TAG = "KeydroidxCrashReporter";

    private static final String PREFS = "nokia_crash_report";
    private static final String KEY_ENABLED = "auto_upload_enabled";
    private static final String KEY_LAST_ATTEMPT = "last_attempt_ms";
    private static final String KEY_QUOTA_DAY = "quota_day";
    private static final String KEY_QUOTA_COUNT = "quota_count";

    /** 待上传标记文件名（放在日志目录内，随日志 zip 一起上传）。 */
    public static final String MARKER_NAME = "pending_report.json";
    /** 两次自动上传的最小间隔（毫秒）。 */
    public static final long MIN_INTERVAL_MS = 60 * 1000L;
    /** 每天自动上传次数上限（服务端 /upload 为 20 次/天，留足余量）。 */
    public static final int MAX_PER_DAY = 10;

    /** 自动上报时填充到 meta.contact 的占位联系方式（服务端要求 1~100 字符）。 */
    private static final String AUTO_CONTACT = "自动上报";
    /** 标记内保存的堆栈最大长度（字符）。 */
    private static final int MAX_STACK_CHARS = 4000;
    /**
     * meta.comment 的字节上限。服务端按 <b>UTF-8 字节</b> 校验（Rust {@code s.len()}，
     * 上限 500），超限即判协议违规并掐断 TCP（客户端表现为 EOFException）；
     * 而 SDK 的 {@code buildMetaJson} 只按字符截断，中文一个字 3 字节，故这里自行留出余量。
     */
    private static final int MAX_COMMENT_BYTES = 480;
    /** comment 内单个「类型/信息/详情」值的 UTF-8 字节上限。 */
    private static final int MAX_MESSAGE_BYTES = 160;

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean sInstalled = new AtomicBoolean(false);
    private static final Object sMarkerLock = new Object();

    /** install() 时缓存的 Application 级 Context（崩溃路径上禁止再取 Context）。 */
    private static volatile Context sAppContext;
    /** 缓存的开关状态：崩溃路径上不读 SP，避免同步 I/O。 */
    private static volatile boolean sEnabled = true;

    private KeydroidxCrashReporter() {
    }

    // ==========================================
    // 对外 API
    // ==========================================

    /**
     * 注册错误/崩溃标记（只注册、不上传，任何进程都可调用）。
     *
     * <p>调用后本进程内任意 {@link KeydroidxLog#e} 与未捕获异常都会落「待上传」标记。
     * 可重复调用（幂等）。</p>
     */
    public static void install(Context context) {
        try {
            Context app = appContextOf(context);
            if (app == null) {
                return;
            }
            sAppContext = app;
            sEnabled = prefs(app).getBoolean(KEY_ENABLED, true);
            if (sInstalled.compareAndSet(false, true)) {
                KeydroidxLog.setErrorMarker(MARKER);
                KeydroidxLog.i(TAG, "crash marker installed, autoUpload=" + sEnabled);
            }
        } catch (Throwable t) {
            KeydroidxLog.w(TAG, "install failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * 若存在「待上传」标记则自动上传日志，成功后重置标记（异步，不阻塞调用方）。
     *
     * <p><b>只在主进程调用</b>，避免多进程重复上传。未初始化
     * {@link KeydroidxFeedback}、开关关闭、被节流时静默跳过（标记保留）。</p>
     */
    public static void uploadPendingIfAny(Context context) {
        final Context app = appContextOf(context);
        if (app == null) {
            return;
        }
        if (sAppContext == null) {
            sAppContext = app;
        }
        try {
            if (!isAutoUploadEnabled(app)) {
                KeydroidxLog.d(TAG, "auto upload disabled, skip pending report");
                return;
            }
            sExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    doUploadPending(app);
                }
            });
        } catch (Throwable t) {
            KeydroidxLog.w(TAG, "uploadPendingIfAny failed: " + t.getMessage());
        }
    }

    /**
     * 是否存在尚未上报的错误/崩溃标记（供设置页提示等使用）。
     */
    public static boolean hasPending(Context context) {
        try {
            Context app = appContextOf(context);
            File logDir = app != null ? KeydroidxFeedback.resolveLogDir(app) : null;
            return logDir != null && new File(logDir, MARKER_NAME).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 读取自动上报开关（默认开启）。 */
    public static boolean isAutoUploadEnabled(Context context) {
        Context app = appContextOf(context);
        if (app == null) {
            return true;
        }
        return prefs(app).getBoolean(KEY_ENABLED, true);
    }

    /** 设置自动上报开关并持久化。 */
    public static void setAutoUploadEnabled(Context context, boolean enabled) {
        Context app = appContextOf(context);
        if (app == null) {
            return;
        }
        sEnabled = enabled;
        prefs(app).edit().putBoolean(KEY_ENABLED, enabled).apply();
        KeydroidxLog.i(TAG, "auto upload switch changed: " + enabled);
    }

    /**
     * 清除「待上传」标记（文件 I/O，请勿在主线程调用）。
     */
    public static void clearPending(Context context) {
        try {
            Context app = appContextOf(context);
            File logDir = app != null ? KeydroidxFeedback.resolveLogDir(app) : null;
            if (logDir == null) {
                return;
            }
            deleteQuietly(new File(logDir, MARKER_NAME));
            deleteQuietly(new File(logDir, MARKER_NAME + ".tmp"));
        } catch (Throwable t) {
            KeydroidxLog.w(TAG, "clearPending failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 标记写入（崩溃/错误路径，必须快且绝不抛异常）
    // ==========================================

    private static final LogErrorMarker MARKER = new LogErrorMarker() {
        @Override
        public void onErrorMarked(String category, String detail, Throwable tr) {
            try {
                if (!sEnabled) {
                    return;
                }
                Context app = sAppContext;
                if (app == null) {
                    return;
                }
                markPending(KeydroidxFeedback.resolveLogDir(app), category, detail, tr);
            } catch (Throwable ignored) {
                // 崩溃路径上任何异常都必须吞掉
            }
        }
    };

    /**
     * 落「待上传」标记文件（同步 + fsync，保证进程被杀前已落盘）。
     *
     * <p>已有标记时不重复写（同一会话内多次 ERROR 只写一次）；崩溃（uncaught）是更强的信号，
     * 会覆盖升级已有标记，确保注释里带出真正的崩溃信息。</p>
     */
    static void markPending(File logDir, String category, String detail, Throwable tr) {
        if (logDir == null) {
            return;
        }
        synchronized (sMarkerLock) {
            try {
                File marker = new File(logDir, MARKER_NAME);
                if (marker.isFile() && !LogErrorMarker.CATEGORY_UNCAUGHT.equals(category)) {
                    return;
                }
                if (!logDir.isDirectory() && !logDir.mkdirs()) {
                    return;
                }

                long now = System.currentTimeMillis();
                JSONObject json = new JSONObject();
                json.put("v", 1);
                json.put("category", category != null ? category : "");
                json.put("ts", now);
                json.put("time", formatTime(now));
                json.put("process", currentProcessName());
                json.put("thread", Thread.currentThread().getName());
                json.put("app_version", appVersion());
                json.put("detail", truncate(detail, 200));
                if (tr != null) {
                    json.put("type", tr.getClass().getName());
                    json.put("message", truncate(String.valueOf(tr.getMessage()), 200));
                    json.put("stack", truncate(stackTraceOf(tr), MAX_STACK_CHARS));
                }
                writeAtomic(marker, json.toString());
            } catch (Throwable ignored) {
            }
        }
    }

    /** 原子写入：先写 .tmp 并 fsync，再 rename 覆盖，避免读到半截 JSON。 */
    private static void writeAtomic(File target, String content) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
        } catch (Throwable ignored) {
            deleteQuietly(tmp);
            return;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
        if (tmp.renameTo(target)) {
            return;
        }
        // renameTo 在目标已存在时的行为与平台相关（Linux/Android 的 rename 直接覆盖，
        // Windows/JVM 会失败），这里显式先删目标再重命名，保证两端语义一致。
        deleteQuietly(target);
        if (!tmp.renameTo(target)) {
            deleteQuietly(tmp);
        }
    }

    // ==========================================
    // 自动上传（后台线程）
    // ==========================================

    private static void doUploadPending(Context context) {
        try {
            if (!KeydroidxFeedback.isConfigured()) {
                KeydroidxLog.d(TAG, "pending report exists but feedback not configured, skip");
                return;
            }
            File logDir = KeydroidxFeedback.resolveLogDir(context);
            if (logDir == null) {
                return;
            }
            File marker = new File(logDir, MARKER_NAME);
            if (!marker.isFile()) {
                return;
            }

            SharedPreferences sp = prefs(context);
            if (!allowAttempt(sp)) {
                KeydroidxLog.i(TAG, "pending report upload throttled, retry on a later launch");
                return;
            }

            JSONObject data = readJson(marker);
            final String comment = buildComment(data);
            Map<String, Object> extras = buildExtras(data);

            recordAttempt(sp);
            KeydroidxLog.i(TAG, "auto uploading pending report: " + comment);

            KeydroidxFeedback.submit(context, AUTO_CONTACT, comment, extras, true,
                    new KeydroidxFeedback.Callback() {
                        @Override
                        public void onResult(boolean success) {
                            if (success) {
                                KeydroidxLog.i(TAG, "pending report uploaded, marker cleared");
                                sExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        clearPending(context);
                                    }
                                });
                            } else {
                                KeydroidxLog.w(TAG, "pending report upload failed, marker kept for next launch");
                            }
                        }
                    });
        } catch (Throwable t) {
            KeydroidxLog.w(TAG, "doUploadPending error: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }
    }

    /** 节流：最小间隔 + 每日配额。 */
    private static boolean allowAttempt(SharedPreferences sp) {
        long now = System.currentTimeMillis();
        long last = sp.getLong(KEY_LAST_ATTEMPT, 0L);
        if (last > 0 && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        String today = formatDay(now);
        if (today.equals(sp.getString(KEY_QUOTA_DAY, ""))
                && sp.getInt(KEY_QUOTA_COUNT, 0) >= MAX_PER_DAY) {
            return false;
        }
        return true;
    }

    private static void recordAttempt(SharedPreferences sp) {
        long now = System.currentTimeMillis();
        String today = formatDay(now);
        int count = today.equals(sp.getString(KEY_QUOTA_DAY, ""))
                ? sp.getInt(KEY_QUOTA_COUNT, 0) + 1 : 1;
        sp.edit()
                .putLong(KEY_LAST_ATTEMPT, now)
                .putString(KEY_QUOTA_DAY, today)
                .putInt(KEY_QUOTA_COUNT, count)
                .apply();
    }

    // ==========================================
    // 标记内容 → 上报字段
    // ==========================================

    /** 组装 meta.comment（严格按 UTF-8 字节控制，服务端上限 500 字节）。 */
    static String buildComment(JSONObject data) {
        if (data == null) {
            return "【自动上报】应用上次运行出现异常，已自动附带运行日志（详见附件 " + MARKER_NAME + "）。";
        }
        boolean crash = LogErrorMarker.CATEGORY_UNCAUGHT.equals(data.optString("category", ""));
        StringBuilder sb = new StringBuilder(256);
        sb.append(crash
                ? "【自动上报】应用上次运行崩溃，已自动附带运行日志。"
                : "【自动上报】应用上次运行记录到错误日志，已自动附带运行日志。");
        // 异常消息最长（且多为中英混排），单独按字节压缩，避免把后面几行挤掉
        appendLine(sb, "类型", truncateUtf8(data.optString("type", ""), MAX_MESSAGE_BYTES));
        appendLine(sb, "信息", truncateUtf8(data.optString("message", ""), MAX_MESSAGE_BYTES));
        appendLine(sb, "详情", truncateUtf8(data.optString("detail", ""), MAX_MESSAGE_BYTES));
        appendLine(sb, "时间", data.optString("time", ""));
        appendLine(sb, "线程", data.optString("thread", ""));
        appendLine(sb, "进程", data.optString("process", ""));
        sb.append("\n完整堆栈见附件日志 ").append(MARKER_NAME).append("。");

        String comment = sb.toString();
        String bounded = truncateUtf8(comment, MAX_COMMENT_BYTES);
        if (bounded.length() != comment.length()) {
            KeydroidxLog.w(TAG, "comment 超长已截断（服务端按 UTF-8 字节校验，上限 500）: "
                    + utf8Length(comment) + " -> " + utf8Length(bounded) + " 字节");
        }
        return bounded;
    }

    /** 组装 extras（与设备信息合并后序列化需 ≤4096 字节）。 */
    static Map<String, Object> buildExtras(JSONObject data) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("auto_report", true);
        if (data == null) {
            return extras;
        }
        extras.put("crash_category", data.optString("category", ""));
        extras.put("crash_time", data.optString("time", ""));
        extras.put("crash_process", data.optString("process", ""));
        extras.put("crash_thread", data.optString("thread", ""));
        extras.put("crash_type", truncateUtf8(data.optString("type", ""), MAX_MESSAGE_BYTES));
        extras.put("crash_detail", truncateUtf8(data.optString("detail", ""), MAX_MESSAGE_BYTES));
        extras.put("crash_marker_version", data.optInt("v", 0));
        return extras;
    }

    static JSONObject readJson(File file) {
        InputStream in = null;
        try {
            if (file == null || !file.isFile()) {
                return null;
            }
            in = new FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) file.length());
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ==========================================
    // 小工具
    // ==========================================

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && value.length() > 0) {
            sb.append('\n').append(label).append("：").append(value);
        }
    }

    private static String appVersion() {
        try {
            KeydroidxFeedbackConfig cfg = KeydroidxFeedback.getConfig();
            return cfg != null && cfg.appVersion != null ? cfg.appVersion : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 读取 /proc/self/cmdline 得到当前进程名（如 {@code io.github.cctyl.nokia:midlet}）。 */
    private static String currentProcessName() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/self/cmdline"));
            String line = br.readLine();
            if (line != null) {
                int end = line.indexOf('\0');
                if (end > 0) {
                    line = line.substring(0, end);
                }
                return line;
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return "";
    }

    /** 纯 Java 堆栈字符串（不依赖 android.util.Log，便于单元测试）。 */
    private static String stackTraceOf(Throwable tr) {
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String formatTime(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ts));
    }

    private static String formatDay(long ts) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ts));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 字符串的 UTF-8 字节数（服务端一律按字节校验长度）。 */
    static int utf8Length(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 按 UTF-8 字节上限截断，绝不切坏多字节字符。
     *
     * <p>服务端的长度校验（comment ≤500、extras ≤4096 等）全部基于字节，
     * 而 Java 的 {@code String.length()} 是字符数，中文一个字 3 字节，
     * 直接按字符截断会超限 → 服务端判定协议违规 → 掐断 TCP（客户端报 EOFException）。</p>
     */
    static String truncateUtf8(String s, int maxBytes) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int bytes = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 代理对按 2×3 字节保守估算，宁可少留也不会越界
            int b = c < 0x80 ? 1 : (c < 0x800 ? 2 : 3);
            if (bytes + b > maxBytes) {
                return s.substring(0, i);
            }
            bytes += b;
        }
        return s;
    }

    private static void deleteQuietly(File f) {
        try {
            if (f != null && f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 取 Application 级 Context；attachBaseContext 阶段可能为 null，回退为传入的 Context。 */
    private static Context appContextOf(Context context) {
        if (context == null) {
            return null;
        }
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
