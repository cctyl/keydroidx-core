package io.github.cctyl.nokia.shizuku;

import android.content.Context;

/**
 * mini_shizuku 对外门面（第三方生态应用集成入口）。
 * <p>
 * 用法：在应用启动时 {@link #init(Context)} 注入应用级 Context，随后调用
 * {@link #isRunning()} / {@link #exec(String)} / {@link #execWithOutput(String)}。
 * <p>
 * 仅同签名应用可成功执行（K 由 launcher 的 KeydroidxShizukuProvider 按签名派发）。
 */
public final class MiniShizuku {

    private MiniShizuku() {
    }

    /** 注入应用级 Context。必须在调用其它方法前于 Application.onCreate 调用一次。 */
    public static void init(Context context) {
        MiniShizukuClient.init(context);
    }

    /** 服务是否在线（TCP 端口可连）。不需 K，对任意应用开放。 */
    public static boolean isRunning() {
        return MiniShizukuClient.isRunning();
    }

    /** 静默执行一条 shell 命令（shell 身份）。同签名才成功。 */
    public static boolean exec(String command) {
        return MiniShizukuClient.exec(command);
    }

    /** 执行并读取一行 ack；鉴权失败/超时/ERR 返回 false（拦截器用）。 */
    public static boolean execAcked(String command) {
        return MiniShizukuClient.execAcked(command);
    }

    /** 执行并返回合并的 stdout/stderr；失败/鉴权拒绝返回 {@code null}。 */
    public static String execWithOutput(String command) {
        return MiniShizukuClient.execWithOutput(command);
    }

    /**
     * 丢弃进程内缓存的 K 并重新向 launcher provider 拉取。
     * <p>
     * launcher 被卸载重装或其进程重启会换新 K，旧缓存会导致服务端回
     * {@code ERR:unauthorized}（表现为「签名不匹配」）；调用本方法可立即恢复鉴权，
     * 无需重启本应用。
     *
     * @return 重拉后的 K；launcher 缺失或异签名时为 {@code null}
     */
    public static String refreshKey() {
        return MiniShizukuClient.refreshKey();
    }
}
