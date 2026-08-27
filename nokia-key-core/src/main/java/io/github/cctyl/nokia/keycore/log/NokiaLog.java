package io.github.cctyl.nokia.keycore.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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
 * <b>分级持久化与开关管理</b>（参考原键桌面设计）：
 * <ul>
 *   <li><b>详细日志关闭（默认/日常）</b>：{@link #getFileMinLevel()} 为 {@link Log#ERROR}。
 *       仅记录 ERROR、CRASH 和系统生命周期等关键日志，零性能损耗、日志文件极小。</li>
 *   <li><b>详细日志开启（排查模式）</b>：{@link #getFileMinLevel()} 为 {@link Log#DEBUG}。
 *       记录所有 DEBUG、INFO、WARN、ERROR 业务日志。</li>
 *   <li><b>开关持久化</b>：通过 {@link #isDetailedLogEnabled(Context)} 与
 *       {@link #setDetailedLogEnabled(Context, boolean)} 读写，默认值跟随 Debug/Release 构建设定。</li>
 * </ul>
 * <p>
 * <b>文件日志</b>：{@link #init(Context)} 后，所有日志按天写入
 * {@code /sdcard/Android/data/<package>/log/yyyyMMdd.log}（异步写，不阻塞 UI 线程），
 * 与原键桌面日志路径完全一致；崩溃堆栈通过 {@link #fileCrash(String, Throwable)}
 * 同步落盘。旧日志默认保留 {@link #KEEP_DAYS} 天，初始化时自动清理。
 * <p>
 * <b>与反馈模块对齐</b>：{@link #getDefaultLogDir(Context)} 返回的目录与
 * {@code NokiaFeedback.resolveLogDir()} 默认目录一致，反馈上传时直接打包本工具落盘的日志。
 * <p>
 * <b>崩溃捕获</b>：{@link #installCrashHandler(Context)} 注册链式
 * {@code UncaughtExceptionHandler}，任何未捕获异常先同步写入当日日志，再交给链上原处理器
 * （系统默认弹「已停止运行」/ 宿主自有的上报器），不依赖任何第三方崩溃上报库。
 */
public final class NokiaLog {

    private static final String DEFAULT_TAG = "KeydroidX";
    private static volatile String tag = DEFAULT_TAG;
    private static volatile boolean enabled = true;

    // ---- 配置持久化常量 ----
    private static final String PREF_NAME = "nokia_log_prefs";
    private static final String KEY_DETAILED_LOG = "log_detailed_enabled";

    // ---- 文件日志 ----
    /** 日志保留天数，init 时自动清理超出该天数的历史文件。 */
    public static final int KEEP_DAYS = 7;
    private static final Object FILE_LOCK = new Object();
    private static volatile boolean fileLogEnabled = false;
    /**
     * 文件日志最低级别（Android Log 级别常量）。低于该级别的日志不落盘。
     * 默认按 {@link #isDetailedLogEnabled(Context)} 初始化为 DEBUG 或 ERROR。
     */
    private static volatile int fileMinLevel = Log.ERROR;
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

    public static String getTag() {
        return tag;
    }

    // ---- 详细日志开关与持久化（参考桌面端设计） ----

    /**
     * 是否开启详细文件日志（true=记录 DEBUG 及以上；false=仅记录 ERROR 及崩溃）。
     * <p>
     * 未手动设置过时按构建类型给默认值：
     * <ul>
     *   <li>Debug 构建：默认 {@code true}（记录详细调试日志）</li>
     *   <li>Release 正式构建：默认 {@code false}（日常仅记录 ERROR 和崩溃，保证性能与隐私）</li>
     * </ul>
     *
     * @param context 上下文
     * @return 当前详细日志是否开启
     */
    public static boolean isDetailedLogEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_DETAILED_LOG)) {
            // 默认值：Debug 包开启，Release 包关闭
            return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        return sp.getBoolean(KEY_DETAILED_LOG, false);
    }

    /**
     * 设置详细日志开关并持久化，同时同步更新当前运行时的 {@link #setFileMinLevel(int)}。
     * <p>
     * 宿主设置页中的「详细日志」开关切换时直接调用此方法即可，无需手动调 {@code setFileMinLevel}。
     *
     * @param context 上下文
     * @param enabled true=开启详细日志（记录 DEBUG+）；false=关闭详细日志（仅记录 ERROR+）
     */
    public static void setDetailedLogEnabled(Context context, boolean enabled) {
        if (context != null) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DETAILED_LOG, enabled).apply();
        }
        setFileMinLevel(enabled ? Log.DEBUG : Log.ERROR);
        i("LogConfig", "详细日志开关已更新: " + enabled + ", fileMinLevel=" + (enabled ? "DEBUG" : "ERROR"));
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
     * <p>
     * 日志目录：{@code /sdcard/Android/data/<package>/log}，按天 {@code yyyyMMdd.log}。
     * 初始化时自动调用 {@link #isDetailedLogEnabled(Context)} 确定最低日志级别
     * （Debug 包默认全量 DEBUG 记录，Release 包默认仅 ERROR 记录）。
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

            // 自动根据配置同步文件日志级别
            boolean detailed = isDetailedLogEnabled(context);
            fileMinLevel = detailed ? Log.DEBUG : Log.ERROR;

            Log.i(tag, "文件日志已启用: " + logDir.getAbsolutePath()
                    + " minLevel=" + (fileMinLevel == Log.DEBUG ? "DEBUG" : "ERROR")
                    + " detailed=" + detailed + " keepDays=" + KEEP_DAYS);
            appendAsync(Log.INFO, "SYS", "===== 日志记录启动 ===== 保留最近 " + KEEP_DAYS
                    + " 天 (detailed=" + detailed + ", minLevel="
                    + (fileMinLevel == Log.DEBUG ? "DEBUG" : "ERROR") + ")");
        } catch (Exception e) {
            Log.w(tag, "NokiaLog.init 失败", e);
        }
    }

    /** 当日日志目录（{@code /sdcard/Android/data/<package>/log}），未初始化时返回 null。 */
    public static File getLogDir() {
        return logDir;
    }

    /**
     * 手动设置文件日志最低级别（Android {@link Log} 级别常量，如 {@link Log#DEBUG}/{@link Log#ERROR}）。
     * 通常推荐使用 {@link #setDetailedLogEnabled(Context, boolean)} 进行统一管理。
     */
    public static void setFileMinLevel(int level) {
        fileMinLevel = level;
        Log.i(tag, "setFileMinLevel: " + level);
    }

    public static int getFileMinLevel() {
        return fileMinLevel;
    }

    // ---- 日志 API ----

    public static void v(String sub, String msg) {
        if (enabled) Log.v(tag, "[" + sub + "] " + msg);
        appendAsync(Log.VERBOSE, sub, msg);
    }

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

    public static void w(String sub, String msg, Throwable t) {
        if (enabled) Log.w(tag, "[" + sub + "] " + msg, t);
        if (!fileLogEnabled || fileHandler == null || Log.WARN < fileMinLevel) return;
        final StringBuilder sb = new StringBuilder();
        sb.append('[').append(LINE_TS.format(new Date())).append("][WARN][").append(sub).append("] ")
                .append(msg).append('\n');
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        final String line = sb.toString();
        fileHandler.post(() -> writeLine(line));
    }

    public static void e(String sub, String msg) {
        if (enabled) Log.e(tag, "[" + sub + "] " + msg);
        appendAsync(Log.ERROR, sub, msg);
    }

    public static void e(String sub, String msg, Throwable t) {
        if (enabled) Log.e(tag, "[" + sub + "] " + msg, t);
        if (!fileLogEnabled || fileHandler == null || Log.ERROR < fileMinLevel) return;
        final StringBuilder sb = new StringBuilder();
        sb.append('[').append(LINE_TS.format(new Date())).append("][ERROR][").append(sub).append("] ")
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

    // ---- 文件写入 ----

    private static void appendAsync(int level, String sub, String msg) {
        if (!fileLogEnabled || fileHandler == null) return;
        if (level < fileMinLevel) return;
        String levelTag;
        switch (level) {
            case Log.VERBOSE: levelTag = "VERBOSE"; break;
            case Log.DEBUG:   levelTag = "DEBUG"; break;
            case Log.INFO:    levelTag = "INFO"; break;
            case Log.WARN:    levelTag = "WARN"; break;
            case Log.ERROR:   levelTag = "ERROR"; break;
            default:          levelTag = "LOG"; break;
        }
        final String line = "[" + LINE_TS.format(new Date()) + "][" + levelTag + "][" + sub + "] " + msg + "\n";
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
