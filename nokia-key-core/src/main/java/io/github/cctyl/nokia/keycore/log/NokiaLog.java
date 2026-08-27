package io.github.cctyl.nokia.keycore.log;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * KeydroidX 生态统一日志工具（纯 Java，零第三方依赖）。
 * <p>
 * 移植自原键桌面 {@code ru.playsoftware.j2meloader.nokia.NokiaLog}，供所有接入
 * {@code keydroidx-core} 的宿主 App 共用。所有调试输出都走这里，统一 TAG 与格式
 * （{@code [子类] 消息}），并可通过 {@link #setEnabled(boolean)} 全局开关控制 logcat 输出。
 * <p>
 * <b>文件日志</b>：{@link #init(Context)} 后，所有日志按天写入
 * {@code /sdcard/Android/data/<package>/log/yyyyMMdd.log}（异步写，不阻塞 UI 线程），
 * 与原键桌面日志路径完全一致；崩溃堆栈通过 {@link #fileCrash(String, Throwable)}
 * 同步落盘。旧日志默认保留 {@link #KEEP_DAYS} 天，初始化时自动清理。
 * <p>
 * <b>与反馈模块对齐</b>：{@link #getDefaultLogDir(Context)} 返回的目录与
 * {@code NokiaFeedback.resolveLogDir()} 默认目录一致，反馈上传时可直接打包本工具落盘的日志。
 * <p>
 * <b>崩溃捕获</b>：{@link #installCrashHandler(Context)} 注册链式
 * {@code UncaughtExceptionHandler}，任何未捕获异常先同步写入当日日志，再交给链上原处理器
 * （系统默认弹「已停止运行」/ 宿主自有的上报器），不依赖任何第三方崩溃上报库。
 */
public final class NokiaLog {

    private static final String DEFAULT_TAG = "KeydroidX";
    private static volatile String tag = DEFAULT_TAG;
    private static volatile boolean enabled = true;

    // ---- 文件日志 ----
    /** 日志保留天数，init 时自动清理超出该天数的历史文件。 */
    public static final int KEEP_DAYS = 7;
    private static final Object FILE_LOCK = new Object();
    private static volatile boolean fileLogEnabled = false;
    /** 文件日志最低级别（Android Log 级别）。低于该级别的日志不落盘；默认 DEBUG 全记录。 */
    private static volatile int fileMinLevel = Log.DEBUG;
    private static File logDir;
    private static HandlerThread fileThread;
    private static Handler fileHandler;
    private static String curDateKey = "";
    private static FileWriter curWriter;
    private static final SimpleDateFormat DATE_KEY = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final SimpleDateFormat LINE_TS = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private NokiaLog() {
    }

    // ---- logcat 开关 ----

    /** 全局开关。默认开启（调试）。关闭后所有 d/i/w/e 不再输出到 logcat。 */
    public static void setEnabled(boolean e) {
        enabled = e;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 自定义 logcat/文件统一 TAG（默认 {@value #DEFAULT_TAG}）。 */
    public static void setTag(String t) {
        tag = t != null && t.length() > 0 ? t : DEFAULT_TAG;
    }

    // ---- 文件日志 ----

    /**
     * 统一约定日志目录：{@code /sdcard/Android/data/<package>/log}。
     * 与原键桌面 {@code NokiaLog} 路径完全一致，{@code NokiaFeedback} 反馈上传默认也读此目录。
     * 外部存储不可用时返回 null。
     */
    public static File getDefaultLogDir(Context context) {
        if (context == null) return null;
        File external = context.getExternalFilesDir(null);
        if (external == null) return null;
        return new File(external.getParentFile(), "log");
    }

    /**
     * 初始化文件日志（应在 Application 早期、主进程调用一次，幂等）。
     * 日志目录：{@code /sdcard/Android/data/<package>/log}，按天 {@code yyyyMMdd.log}。
     * 默认 TAG 为 {@value #DEFAULT_TAG}，如需自定义可在 init 前调 {@link #setTag(String)}。
     */
    public static synchronized void init(Context context) {
        try {
            if (fileLogEnabled || context == null) return;
            File dir = getDefaultLogDir(context);
            if (dir == null) {
                Log.w(tag, "外部存储不可用，跳过文件日志初始化");
                return;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(tag, "日志目录创建失败: " + dir.getAbsolutePath());
                return;
            }
            logDir = dir;
            cleanOldLogs();
            fileThread = new HandlerThread("NokiaLogFile");
            fileThread.start();
            fileHandler = new Handler(fileThread.getLooper());
            fileLogEnabled = true;
            // 默认全级别记录；宿主可通过 setFileMinLevel 收紧（如仅 ERROR 及以上）
            fileMinLevel = Log.DEBUG;
            Log.i(tag, "文件日志已启用: " + logDir.getAbsolutePath()
                    + " minLevel=" + fileMinLevel + " keepDays=" + KEEP_DAYS);
            appendAsync(Log.INFO, "SYS", "===== 日志记录启动 ===== 保留最近 " + KEEP_DAYS + " 天");
        } catch (Exception e) {
            Log.w(tag, "NokiaLog.init 失败", e);
        }
    }

    /** 当日日志目录（{@code /sdcard/Android/data/<package>/log}），未初始化时返回 null。 */
    public static File getLogDir() {
        return logDir;
    }

    /**
     * 设置文件日志最低级别（Android {@link Log} 级别常量，如 {@link Log#DEBUG}/{@link Log#ERROR}）。
     * 宿主设置页「日志记录」切换时调用，实时生效：
     * 开启=DEBUG（全级别），关闭=ERROR（及以上）。
     */
    public static void setFileMinLevel(int level) {
        fileMinLevel = level;
        Log.i(tag, "setFileMinLevel: " + level);
    }

    public static int getFileMinLevel() {
        return fileMinLevel;
    }

    // ---- 日志 API ----

    public static void d(String sub, String msg) {
        if (enabled) Log.d(tag, "[" + sub + "] " + msg);
        appendAsync(Log.DEBUG, sub, msg);
    }

    public static void i(String sub, String msg) {
        if (enabled) Log.i(tag, "[" + sub + "] " + msg);
        appendAsync(Log.INFO, sub, msg);
    }

    public static void w(String sub, String msg) {
        if (enabled) Log.w(tag, "[" + sub + "] " + msg);
        appendAsync(Log.WARN, sub, msg);
    }

    public static void e(String sub, String msg) {
        if (enabled) Log.e(tag, "[" + sub + "] " + msg);
        appendAsync(Log.ERROR, sub, msg);
    }

    public static void e(String sub, String msg, Throwable t) {
        if (enabled) Log.e(tag, "[" + sub + "] " + msg, t);
        if (!fileLogEnabled || fileHandler == null || Log.ERROR < fileMinLevel) return;
        final StringBuilder sb = new StringBuilder();
        sb.append('[').append(LINE_TS.format(new Date())).append("][").append(sub).append("] ")
                .append(msg).append('\n');
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        final String line = sb.toString();
        fileHandler.post(() -> writeLine(line));
    }

    /**
     * 崩溃堆栈同步写入当日日志（进程即将终止，必须同步落盘后再交给系统处理）。
     * 不受日志记录开关影响：崩溃（FATAL）始终记录。
     */
    public static void fileCrash(String msg, Throwable t) {
        if (!fileLogEnabled || logDir == null) return;
        synchronized (FILE_LOCK) {
            try {
                FileWriter w = openDailyWriter();
                if (w == null) return;
                w.write("[" + LINE_TS.format(new Date()) + "][FATAL] " + msg + "\n");
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    w.write(sw.toString() + "\n");
                }
                w.flush();
                closeWriter();
            } catch (Exception ignored) {
                // 日志写入失败静默，避免日志自身引发崩溃
            }
        }
    }

    // ---- 崩溃捕获 ----

    /**
     * 注册全局未捕获异常处理器：任何未捕获异常先同步写入当日日志，
     * 再交给链上原有处理器（系统默认弹「已停止运行」/ 宿主自有的上报器）。
     * 可多次调用：每次以当前默认处理器为链尾，保证本方法始终在最外层。
     * <p>
     * 纯 Java 实现，不依赖 ACRA 等第三方崩溃上报库。需先 {@link #init(Context)} 启用文件日志。
     */
    public static void installCrashHandler(Context context) {
        init(context);
        try {
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                fileCrash("未捕获异常", throwable);
                if (prev != null) {
                    prev.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
            });
            Log.i(tag, "崩溃落盘处理器已安装");
        } catch (Exception e) {
            Log.w(tag, "installCrashHandler 失败", e);
        }
    }

    // ---- logcat 持续捕获 ----

    private static volatile Process logcatProcess;
    private static volatile Thread logcatThread;

    /**
     * 启动 logcat 持续捕获：后台线程持续读取 logcat 输出，写入当日日志文件。
     * <p>
     * 自动捕获宿主 App 内所有 {@code android.util.Log.d/e/i/w} 调用，无需逐个替换为 NokiaLog。
     * 捕获范围按 {@code logcatFilter} 过滤（如 {@code *:I} 记录 INFO 及以上，
     * 或 {@code KeydroidX-Music:V AndroidRuntime:E *:S} 只抓指定 TAG）。
     * <p>
     * <b>权限</b>：需 READ_LOGS 权限（Android 13 可通过
     * {@code adb shell pm grant <包名> android.permission.READ_LOGS} 授予）。
     * 无权限时静默失败并打印警告，不影响 App 运行。
     * <p>
     * 需先 {@link #init(Context)} 启用文件日志。可重复调用，仅首次生效。
     *
     * @param context       上下文
     * @param logcatFilter  logcat 过滤表达式（如 {@code "*:I"} 或 {@code "*:V"}），
     *                      传 null 默认 {@code *:I}
     */
    public static void startLogcatCapture(Context context, String logcatFilter) {
        init(context);
        if (logcatProcess != null) {
            Log.w(tag, "logcat 捕获已启动，忽略重复调用");
            return;
        }
        try {
            String filter = logcatFilter != null && logcatFilter.length() > 0 ? logcatFilter : "*:I";
            // -v threadtime 带线程 ID 与时间戳，便于排查；清除旧缓冲避免重复 dump
            ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "threadtime", filter);
            pb.redirectErrorStream(true);
            logcatProcess = pb.start();
            logcatThread = new Thread(() -> {
                java.io.BufferedReader reader = null;
                try {
                    reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(logcatProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (Thread.interrupted()) break;
                        // 直接复用文件写入管道（带时间戳，但 logcat 自带时间戳，
                        // 这里以 [cat] 前缀标识来源，原始行完整保留）
                        final String fline = "[cat] " + line + "\n";
                        if (fileHandler != null) {
                            fileHandler.post(() -> writeLine(fline));
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception ignored) {}
                }
            }, "NokiaLogcat");
            logcatThread.setDaemon(true);
            logcatThread.start();
            Log.i(tag, "logcat 持续捕获已启动: filter=" + filter);
        } catch (Exception e) {
            Log.w(tag, "startLogcatCapture 失败（可能缺少 READ_LOGS 权限）", e);
        }
    }

    /** 停止 logcat 持续捕获（通常不需要手动调用，进程退出时自动销毁）。 */
    public static void stopLogcatCapture() {
        try {
            if (logcatThread != null) {
                logcatThread.interrupt();
                logcatThread = null;
            }
            if (logcatProcess != null) {
                logcatProcess.destroy();
                logcatProcess = null;
            }
            Log.i(tag, "logcat 捕获已停止");
        } catch (Exception ignored) {}
    }

    // ---- 文件写入 ----

    private static void appendAsync(int level, String sub, String msg) {
        if (!fileLogEnabled || fileHandler == null) return;
        if (level < fileMinLevel) return;
        final String line = "[" + LINE_TS.format(new Date()) + "][" + sub + "] " + msg + "\n";
        fileHandler.post(() -> writeLine(line));
    }

    private static void writeLine(String line) {
        synchronized (FILE_LOCK) {
            try {
                FileWriter w = openDailyWriter();
                if (w == null) return;
                w.write(line);
                w.flush();
            } catch (Exception ignored) {
            }
        }
    }

    /** 按日期切换当日日志文件（调用方需持有 FILE_LOCK）。 */
    private static FileWriter openDailyWriter() throws IOException {
        String key = DATE_KEY.format(new Date());
        if (!key.equals(curDateKey) || curWriter == null) {
            closeWriter();
            curDateKey = key;
            File f = new File(logDir, key + ".log");
            curWriter = new FileWriter(f, true);
        }
        return curWriter;
    }

    private static void closeWriter() {
        if (curWriter != null) {
            try {
                curWriter.close();
            } catch (IOException ignored) {
            }
            curWriter = null;
        }
    }

    /** 删除超过保留天数的历史日志文件。 */
    private static void cleanOldLogs() {
        try {
            File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600 * 1000;
            for (File f : files) {
                if (f.lastModified() < cutoff) {
                    f.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 把 keyCode 转成面向用户的中文键名（日志与 UI 通用）。 */
    public static String keyName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:      return "上";
            case KeyEvent.KEYCODE_DPAD_DOWN:    return "下";
            case KeyEvent.KEYCODE_DPAD_LEFT:    return "左";
            case KeyEvent.KEYCODE_DPAD_RIGHT:   return "右";
            case KeyEvent.KEYCODE_DPAD_CENTER:  return "确认";
            case KeyEvent.KEYCODE_ENTER:        return "确定";
            case KeyEvent.KEYCODE_SPACE:        return "空格";
            case KeyEvent.KEYCODE_BUTTON_A:     return "A";
            case KeyEvent.KEYCODE_SOFT_LEFT:    return "左软键";
            case KeyEvent.KEYCODE_SOFT_RIGHT:   return "右软键";
            case KeyEvent.KEYCODE_MENU:         return "菜单";
            case KeyEvent.KEYCODE_BACK:         return "返回";
            case KeyEvent.KEYCODE_ENDCALL:      return "挂机";
            case KeyEvent.KEYCODE_CALL:         return "通话";
            case KeyEvent.KEYCODE_CAMERA:       return "相机";
            case KeyEvent.KEYCODE_VOLUME_UP:    return "音量加";
            case KeyEvent.KEYCODE_VOLUME_DOWN:  return "音量减";
            case KeyEvent.KEYCODE_POWER:        return "电源";
            case KeyEvent.KEYCODE_HOME:         return "Home";
            case KeyEvent.KEYCODE_STAR:         return "*号";
            case KeyEvent.KEYCODE_POUND:        return "井号";
            case KeyEvent.KEYCODE_DEL:          return "删除";
            case KeyEvent.KEYCODE_CLEAR:        return "清除";
            case KeyEvent.KEYCODE_0:            return "0";
            case KeyEvent.KEYCODE_1:            return "1";
            case KeyEvent.KEYCODE_2:            return "2";
            case KeyEvent.KEYCODE_3:            return "3";
            case KeyEvent.KEYCODE_4:            return "4";
            case KeyEvent.KEYCODE_5:            return "5";
            case KeyEvent.KEYCODE_6:            return "6";
            case KeyEvent.KEYCODE_7:            return "7";
            case KeyEvent.KEYCODE_8:            return "8";
            case KeyEvent.KEYCODE_9:            return "9";
            case KeyEvent.KEYCODE_UNKNOWN:      return "未绑定";
            default:
                if (Build.VERSION.SDK_INT >= 29) {
                    return KeyEvent.keyCodeToString(keyCode);
                }
                return "KEYCODE_" + keyCode;
        }
    }
}
