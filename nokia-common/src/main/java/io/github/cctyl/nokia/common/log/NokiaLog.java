package io.github.cctyl.nokia.common.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诺基亚生态统一日志记录器。
 * <p>
 * 特性：
 * <ul>
 *   <li><b>零第三方依赖</b>：纯 Android SDK + Java 8 实现。</li>
 *   <li><b>双通道分发</b>：同时输出到系统 Logcat 与本地日志文件。</li>
 *   <li><b>分级持久化控制</b>：默认仅持久化 {@link Log#ERROR} 及以上日志，减少磁盘 I/O；开启详细日志时持久化 {@link Log#DEBUG} 及以上。</li>
 *   <li><b>异步无锁写入</b>：专用 {@link HandlerThread} 批量缓冲写入，绝不阻塞 UI 线程。</li>
 *   <li><b>自动生命周期管理</b>：按天分文件存储（{@code yyyyMMdd.log}），自动保留最近 7 天日志，超期自动删除。</li>
 *   <li><b>全局崩溃自动捕获</b>：可选挂载未捕获异常处理器，崩溃堆栈自动落盘。</li>
 * </ul>
 */
public class NokiaLog {

    private static final String PREF_NAME = "nokia_log_prefs";
    private static final String KEY_DETAILED_LOG_ENABLED = "pref_detailed_log_enabled";
    public static final String DEFAULT_TAG = "NokiaLog";
    private static final int MAX_LOG_DAYS = 7;

    private static volatile boolean sInitialized = false;
    private static File sLogDir;
    private static HandlerThread sWriteThread;
    private static WriteHandler sWriteHandler;
    private static volatile int sFileMinLevel = Log.ERROR;
    private static volatile String sDefaultTag = DEFAULT_TAG;

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMdd", Locale.US);

    protected NokiaLog() {
        // utility class
    }

    /**
     * 初始化日志系统（使用默认日志目录：{@code Context.getExternalFilesDir("log")} 或内部缓存目录）。
     * 会自动根据 SharedPreferences 中的详细日志开关恢复持久化等级。
     *
     * @param context 上下文（建议 Application）
     */
    public static synchronized void init(@NonNull Context context) {
        init(context, getDefaultLogDir(context));
    }

    /**
     * 初始化日志系统并指定自定义日志目录。
     *
     * @param context 上下文
     * @param logDir  自定义日志存储目录
     */
    public static synchronized void init(@NonNull Context context, @NonNull File logDir) {
        if (sInitialized) return;

        try {
            // attachBaseContext() 阶段 getApplicationContext() 尚为 null（Application 还未挂到 LoadedApk），
            // 此时直接用传入的 Context——它的 ContextImpl 已可用，getSharedPreferences/getExternalFilesDir 正常。
            Context appCtx = appContext(context);
            sLogDir = logDir;
            if (!sLogDir.exists()) {
                sLogDir.mkdirs();
            }

            // 恢复分级设置：如果 SP 中已配置则读取；未配置时若为 Debug 构建则默认开启详细日志
            SharedPreferences sp = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean isDebugBuild = (appCtx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            boolean detailed = sp.getBoolean(KEY_DETAILED_LOG_ENABLED, isDebugBuild);
            sFileMinLevel = detailed ? Log.DEBUG : Log.ERROR;

            // 启动后台写入线程
            sWriteThread = new HandlerThread("NokiaLogWriter");
            sWriteThread.start();
            sWriteHandler = new WriteHandler(sWriteThread.getLooper());

            sInitialized = true;

            // 清理超期日志（后台执行）
            cleanOldLogsAsync();

            Log.i("NokiaLog", "NokiaLog initialized: dir=" + sLogDir.getAbsolutePath()
                    + ", detailed=" + detailed + ", fileMinLevel=" + levelToString(sFileMinLevel));
        } catch (Throwable t) {
            // 日志系统初始化失败绝不能拖垮宿主（桌面场景下表现为「进不了桌面」），降级为仅 logcat。
            sInitialized = false;
            sLogDir = null;
            sWriteHandler = null;
            Log.w("NokiaLog", "NokiaLog init failed, file logging disabled", t);
        }
    }

    /** 取 Application Context；attachBaseContext 阶段为 null 时回退为传入的 Context。 */
    private static Context appContext(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        return appCtx != null ? appCtx : context;
    }

    /**
     * 获取生态统一约定的默认日志存储目录：
     * 优先使用 {@code /sdcard/Android/data/<package>/files/log}，回退使用内部存储 {@code /data/data/<package>/files/log}。
     */
    @NonNull
    public static File getDefaultLogDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir("log");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "log");
        }
        return dir;
    }

    /**
     * 获取当前已初始化的日志目录（{@link #init} 时传入/推导的目录）。
     * 未初始化时返回 null。
     */
    @Nullable
    public static File getLogDir() {
        return sLogDir;
    }

    /**
     * 获取当前是否开启了详细日志模式（开启时持久化 DEBUG 及以上日志；关闭时仅持久化 ERROR 及以上）。
     */
    public static boolean isDetailedLogEnabled(@NonNull Context context) {
        Context appCtx = appContext(context);
        SharedPreferences sp = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isDebugBuild = (appCtx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return sp.getBoolean(KEY_DETAILED_LOG_ENABLED, isDebugBuild);
    }

    /**
     * 动态设置是否开启详细日志模式，并同步持久化到 SharedPreferences。
     *
     * @param context 上下文
     * @param enabled true: 记录 DEBUG/INFO/WARN/ERROR; false: 仅记录 ERROR
     */
    public static void setDetailedLogEnabled(@NonNull Context context, boolean enabled) {
        Context appCtx = appContext(context);
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DETAILED_LOG_ENABLED, enabled)
                .apply();

        sFileMinLevel = enabled ? Log.DEBUG : Log.ERROR;
        Log.i("NokiaLog", "Detailed log switch changed: " + enabled
                + " (fileMinLevel=" + levelToString(sFileMinLevel) + ")");
    }

    /**
     * 获取当前文件持久化的最低等级。
     */
    public static int getFileMinLevel() {
        return sFileMinLevel;
    }

    /**
     * 直接设置文件持久化的最低等级（不改变 SP 配置）。
     */
    public static void setFileMinLevel(int minLevel) {
        sFileMinLevel = minLevel;
    }

    /** 自定义 logcat/文件统一 TAG（默认 {@value #DEFAULT_TAG}）。 */
    public static void setTag(String t) {
        sDefaultTag = t != null && t.length() > 0 ? t : DEFAULT_TAG;
    }

    public static String getTag() {
        return sDefaultTag;
    }

    /**
     * 安装未捕获异常（Crash）自动捕获处理器，崩溃时将堆栈自动写入日志文件。
     */
    public static void installCrashHandler(@NonNull final Context context) {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                fileCrash(t, e);
            } catch (Throwable ignored) {
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e);
            }
        });
    }

    // ==========================================
    // 标准日志分发 API (Logcat + 文件持久化)
    // ==========================================

    public static void v(String tag, String msg) {
        Log.v(tag, msg);
        logToFile(Log.VERBOSE, tag, msg, null);
    }

    public static void v(String tag, String msg, Throwable tr) {
        Log.v(tag, msg, tr);
        logToFile(Log.VERBOSE, tag, msg, tr);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        logToFile(Log.DEBUG, tag, msg, null);
    }

    public static void d(String tag, String msg, Throwable tr) {
        Log.d(tag, msg, tr);
        logToFile(Log.DEBUG, tag, msg, tr);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        logToFile(Log.INFO, tag, msg, null);
    }

    public static void i(String tag, String msg, Throwable tr) {
        Log.i(tag, msg, tr);
        logToFile(Log.INFO, tag, msg, tr);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        logToFile(Log.WARN, tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
        logToFile(Log.WARN, tag, msg, tr);
    }

    public static void w(String tag, Throwable tr) {
        Log.w(tag, tr);
        logToFile(Log.WARN, tag, "", tr);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        logToFile(Log.ERROR, tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
        logToFile(Log.ERROR, tag, msg, tr);
    }

    /**
     * 专门用于记录未捕获异常/重大故障的 API。
     */
    public static void fileCrash(@Nullable Thread thread, @NonNull Throwable throwable) {
        String threadName = (thread != null) ? thread.getName() : Thread.currentThread().getName();
        String msg = "FATAL UNCAUGHT EXCEPTION in thread [" + threadName + "]";
        Log.e("AndroidRuntime", msg, throwable);
        logToFile(Log.ASSERT, "CRASH", msg, throwable);
    }

    // ==========================================
    // 内部实现：日志格式化与异步落盘
    // ==========================================

    private static void logToFile(int priority, String tag, String msg, @Nullable Throwable tr) {
        if (!sInitialized || sWriteHandler == null) return;
        if (priority < sFileMinLevel) return;

        long now = System.currentTimeMillis();
        String logLine = formatLogLine(now, priority, tag, msg, tr);

        Message m = sWriteHandler.obtainMessage(WriteHandler.MSG_WRITE, logLine);
        sWriteHandler.sendMessage(m);
    }

    private static String formatLogLine(long timestamp, int priority, String tag, String msg, @Nullable Throwable tr) {
        StringBuilder sb = new StringBuilder(128);
        Date d = new Date(timestamp);
        synchronized (TIME_FORMAT) {
            sb.append('[').append(TIME_FORMAT.format(d)).append(']');
        }
        sb.append('[').append(levelToChar(priority)).append(']');
        sb.append('[').append(tag != null ? tag : "App").append("] ");
        if (msg != null) {
            sb.append(msg);
        }
        if (tr != null) {
            sb.append('\n');
            StringWriter sw = new StringWriter(256);
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            pw.flush();
            sb.append(sw.toString());
        }
        sb.append('\n');
        return sb.toString();
    }

    private static char levelToChar(int priority) {
        switch (priority) {
            case Log.VERBOSE: return 'V';
            case Log.DEBUG:   return 'D';
            case Log.INFO:    return 'I';
            case Log.WARN:    return 'W';
            case Log.ERROR:   return 'E';
            case Log.ASSERT:  return 'A';
            default:          return 'D';
        }
    }

    private static String levelToString(int priority) {
        switch (priority) {
            case Log.VERBOSE: return "VERBOSE";
            case Log.DEBUG:   return "DEBUG";
            case Log.INFO:    return "INFO";
            case Log.WARN:    return "WARN";
            case Log.ERROR:   return "ERROR";
            case Log.ASSERT:  return "ASSERT";
            default:          return "UNKNOWN(" + priority + ")";
        }
    }

    private static void cleanOldLogsAsync() {
        if (sWriteHandler == null) return;
        sWriteHandler.post(() -> {
            try {
                if (sLogDir == null || !sLogDir.exists()) return;
                File[] files = sLogDir.listFiles((dir, name) -> name.endsWith(".log"));
                if (files == null || files.length <= MAX_LOG_DAYS) return;

                long now = System.currentTimeMillis();
                long maxAgeMs = (long) MAX_LOG_DAYS * 24 * 60 * 60 * 1000;
                for (File f : files) {
                    if (now - f.lastModified() > maxAgeMs) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static class WriteHandler extends Handler {
        private static final int MSG_WRITE = 1;
        private String mCurrentDateStr = "";
        private File mCurrentFile = null;
        private BufferedWriter mWriter = null;

        WriteHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_WRITE && msg.obj instanceof String) {
                String line = (String) msg.obj;
                writeLine(line);
            }
        }

        private void writeLine(String line) {
            try {
                Date now = new Date();
                String dateStr;
                synchronized (DATE_FORMAT) {
                    dateStr = DATE_FORMAT.format(now);
                }

                if (!dateStr.equals(mCurrentDateStr) || mWriter == null) {
                    closeWriter();
                    mCurrentDateStr = dateStr;
                    mCurrentFile = new File(sLogDir, dateStr + ".log");
                    mWriter = new BufferedWriter(new FileWriter(mCurrentFile, true));
                }

                mWriter.write(line);
                mWriter.flush();
            } catch (IOException e) {
                Log.e("NokiaLog", "Failed to write log to file", e);
                closeWriter();
            }
        }

        private void closeWriter() {
            if (mWriter != null) {
                try {
                    mWriter.close();
                } catch (IOException ignored) {
                }
                mWriter = null;
            }
        }
    }
}
