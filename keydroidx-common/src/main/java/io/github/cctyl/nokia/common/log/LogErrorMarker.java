package io.github.cctyl.nokia.common.log;

/**
 * 「错误标记」回调：{@link KeydroidxLog} 在记录 {@link android.util.Log#ERROR} 级日志
 * 或未捕获崩溃（{@link KeydroidxLog#fileCrash}）时同步回调给注册方。
 *
 * <p>设计目的：日志器本身只负责「记录」，不感知「标记文件 / 上报」这类业务语义；
 * 生态中的 {@code io.github.cctyl.nokia.common.feedback.KeydroidxCrashReporter}
 * 实现本接口，把「本进程出现过错误/崩溃」落成待上传标记，供下次启动自动上传日志。</p>
 *
 * <p><b>实现要求</b>：回调发生在崩溃线程上且是同步调用，实现必须
 * 「快、绝不抛异常、绝不阻塞」——尤其 {@link #CATEGORY_UNCAUGHT} 场景下进程随时会被杀死。</p>
 */
public interface LogErrorMarker {

    /** 未捕获异常 / Error（进程即将被杀，堆栈随时可能丢失）。 */
    String CATEGORY_UNCAUGHT = "uncaught_exception";

    /** 业务显式记录的 ERROR 级日志（{@link KeydroidxLog#e}）。 */
    String CATEGORY_ERROR = "error_log";

    /**
     * 记录到「错误/崩溃」事件。
     *
     * @param category {@link #CATEGORY_UNCAUGHT} 或 {@link #CATEGORY_ERROR}
     * @param detail   人类可读的摘要（如 {@code "Tag: message"}、{@code "uncaught exception on thread [main]"}）
     * @param tr       异常对象；非崩溃事件可能为 null
     */
    void onErrorMarked(String category, String detail, Throwable tr);
}
