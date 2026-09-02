package io.github.cctyl.nokia.shizuku;

/**
 * mini_shizuku 客户端常量与协议定义（client 侧，与 server 端 {@code MsgProcess} 约定一致，无共享类）。
 * <p>
 * 传输：TCP {@code 127.0.0.1:10500}。
 * 行协议 v3：每条命令行 = {@code <K>|<inner>}，{@code <inner>} 为原协议串（{@code EXEC|cmd} /
 * {@code EXEC_OUT|cmd} 等）。{@code K} 由 launcher 的 {@code NokiaShizukuProvider} 派发，
 * 仅同签名应用可获取（见 {@link MiniShizukuClient#getKey}）。
 */
public final class MiniShizukuConst {

    private MiniShizukuConst() {
    }

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 10500;

    /** 连接探测超时（{@link #isRunning()} 用）。 */
    public static final int CONNECT_TIMEOUT = 500;
    /** 读响应超时（exec 拦截器 ack / execWithOutput）。 */
    public static final int READ_TIMEOUT = 3000;

    // inner 协议前缀（与 server MsgProcess 一致）
    public static final String PREFIX_SILENT = "EXEC|";
    public static final String PREFIX_OUTPUT = "EXEC_OUT|";
    public static final String EXIT_PREFIX = "EXIT:";

    /**
     * launcher 的 ContentProvider authority 后缀。
     * 完整 authority = launcherPackage + "." + 此后缀。
     * server 与 client 都按此拼装。
     */
    public static final String AUTHORITY_SUFFIX = ".shizuku";

    // provider call 方法名
    public static final String METHOD_GET_SERVER_KEY = "getServerKey";
    public static final String METHOD_GET_KEY = "getKey";

    /** Bundle 中承载 K 的 key。 */
    public static final String EXTRA_KEY = "k";

    /** launcher 候选包名（正式版 / 调试版），client 按 authority 解析实际包名。 */
    public static final String[] LAUNCHER_PACKAGES = {
            "io.github.cctyl.nokia",
            "io.github.cctyl.nokia.debug"
    };
}
