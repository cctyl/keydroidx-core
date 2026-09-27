package io.github.cctyl.nokia.shizuku;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;

/**
 * mini_shizuku 客户端：TCP 10500 + 密钥 K。
 * <p>
 * K 的获取：调用 launcher 的 {@code KeydroidxShizukuProvider.call("getKey")}。provider 按
 * {@code getCallingUid()} 校验签名（同 launcher 签名才发 K），故 K 只能被同签名应用拿到。
 * 第三方应用与 launcher 自身走同一路径（launcher 自身 uid 落在同签名分支，正常返回 K）。
 * <p>
 * 协议：每条命令行 = {@code <K>|<inner>}；{@link #isRunning()} 仅 TCP 连接探测，不发数据、不需 K。
 */
public final class MiniShizukuClient {

    private static final String TAG = "MiniShizuku";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** 进程级缓存：解析到的 launcher 包名（authority 前缀）。 */
    private static String sLauncherPackage;
    /** 进程级缓存：从 provider 拿到的 K（同签名才有值）。null 表示未取过或被拒。 */
    private static volatile String sKey;
    private static Context sAppContext;

    /**
     * 服务端因 K 失效而拒绝（launcher 重启/重装会换新 K，旧缓存必须丢弃重拉）。
     * 与 server 端 {@code MsgProcess} / {@code ServerEnv.verify} 的应答一致。
     */
    private static final String ERR_UNAUTHORIZED = "ERR:unauthorized";

    // execAcked 单次执行结果
    private static final int ACK_OK = 0;
    private static final int ACK_FAIL = 1;
    private static final int ACK_UNAUTHORIZED = 2;

    private MiniShizukuClient() {
    }

    /** 注入应用级 Context（launcher 在 Application.onCreate 调用；第三方应用同理）。 */
    public static void init(Context context) {
        Context app = context == null ? null : context.getApplicationContext();
        sAppContext = app != null ? app : context;
        Log.i(TAG, "client init: ctx=" + sAppContext);
    }

    /**
     * 探测 mini_shizuku 服务是否在线（能否连上 TCP 端口）。
     * 不需要 K，对任意应用开放（仅探测端口在线与否，无副作用）。
     */
    public static boolean isRunning() {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(MiniShizukuConst.HOST, MiniShizukuConst.PORT),
                    MiniShizukuConst.CONNECT_TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(socket);
        }
    }

    /** 静默执行（不回读输出）。 */
    public static boolean exec(String command) {
        String k = getKey();
        if (k == null) return false;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(MiniShizukuConst.HOST, MiniShizukuConst.PORT),
                    MiniShizukuConst.CONNECT_TIMEOUT);
            OutputStream out = socket.getOutputStream();
            out.write((k + "|" + MiniShizukuConst.PREFIX_SILENT + command + "\n").getBytes(UTF8));
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * 执行并读取服务端一行 ack（{@code OK:..} / {@code ERR:..}）。
     * 用于拦截器等需要确认是否真正生效的命令。鉴权失败、超时、ERR 均返回 false。
     * <p>
     * 服务端回 {@code ERR:unauthorized}（launcher 重启/重装换了 K）时会丢弃缓存的 K
     * 重拉一次再试，成功则本次仍返回 true。
     */
    public static boolean execAcked(String command) {
        int r = execAckedOnce(command, getKey());
        if (r == ACK_UNAUTHORIZED) {
            r = execAckedOnce(command, refreshKey());
        }
        return r == ACK_OK;
    }

    private static int execAckedOnce(String command, String k) {
        if (k == null) return ACK_FAIL;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(MiniShizukuConst.HOST, MiniShizukuConst.PORT),
                    MiniShizukuConst.CONNECT_TIMEOUT);
            OutputStream out = socket.getOutputStream();
            out.write((k + "|" + MiniShizukuConst.PREFIX_SILENT + command + "\n").getBytes(UTF8));
            out.flush();
            socket.setSoTimeout(MiniShizukuConst.READ_TIMEOUT);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), UTF8));
            try {
                String line = reader.readLine();
                if (line == null) return ACK_FAIL;
                if (ERR_UNAUTHORIZED.equals(line.trim())) return ACK_UNAUTHORIZED;
                return line.startsWith("ERR:") ? ACK_FAIL : ACK_OK;
            } catch (java.net.SocketTimeoutException e) {
                // 老服务端不回 ack：写入成功即视为成功
                return ACK_OK;
            }
        } catch (IOException e) {
            return ACK_FAIL;
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * 执行并回读输出，直到 {@code EXIT:<code>}。
     * 鉴权失败或 IO 异常返回 null。
     * <p>
     * 服务端回 {@code ERR:unauthorized}（launcher 重启/重装换了 K）时会丢弃缓存的 K
     * 重拉一次再试。
     */
    public static String execWithOutput(String command) {
        Reply r = execWithOutputOnce(command, getKey());
        if (r.unauthorized) {
            r = execWithOutputOnce(command, refreshKey());
        }
        return r.body;
    }

    private static Reply execWithOutputOnce(String command, String k) {
        if (k == null) return new Reply(null, false);
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(MiniShizukuConst.HOST, MiniShizukuConst.PORT),
                    MiniShizukuConst.CONNECT_TIMEOUT);
            OutputStream out = socket.getOutputStream();
            out.write((k + "|" + MiniShizukuConst.PREFIX_OUTPUT + command + "\n").getBytes(UTF8));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), UTF8));
            String first = reader.readLine();
            if (first != null && first.startsWith("ERR:")) {
                // 鉴权失败（K 失效时标记 unauthorized，由调用方丢弃缓存重拉重试）
                return new Reply(null, ERR_UNAUTHORIZED.equals(first.trim()));
            }
            StringBuilder sb = new StringBuilder();
            if (first != null) {
                if (first.startsWith(MiniShizukuConst.EXIT_PREFIX)) {
                    return new Reply("", false); // 无输出，直接结束
                }
                // execWithOutput 的输出第一行若为 OK:（拦截器走 execAcked，不会到这），
                // 此处仅处理普通命令输出
                sb.append(first).append('\n');
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(MiniShizukuConst.EXIT_PREFIX)) {
                    break;
                }
                sb.append(line).append('\n');
            }
            return new Reply(sb.toString(), false);
        } catch (IOException e) {
            return new Reply(null, false);
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * 拉取「最近任务」快照：发送 {@code SNAP|<taskId>}，服务端反射取系统快照后
     * 以 {@code OK:BASE64:<jpeg>} 单行回传。
     *
     * @return JPEG 的 base64（不含前缀）；离线 / 鉴权拒绝 / 无快照返回 null
     */
    public static String fetchSnapshot(int taskId) {
        String line = fetchSnapshotOnce(taskId, getKey());
        if (line != null && ERR_UNAUTHORIZED.equals(line)) {
            line = fetchSnapshotOnce(taskId, refreshKey());
        }
        if (line == null || !line.startsWith("OK:BASE64:")) return null;
        return line.substring("OK:BASE64:".length());
    }

    private static String fetchSnapshotOnce(int taskId, String k) {
        if (k == null) return null;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(MiniShizukuConst.HOST, MiniShizukuConst.PORT),
                    MiniShizukuConst.CONNECT_TIMEOUT);
            OutputStream out = socket.getOutputStream();
            out.write((k + "|SNAP|" + taskId + "\n").getBytes(UTF8));
            out.flush();
            // 快照抓取 + JPEG 编码在服务端执行，读超时放宽到 15s
            socket.setSoTimeout(Math.max(MiniShizukuConst.READ_TIMEOUT, 15000));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), UTF8));
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(socket);
        }
    }

    /** 单次 execWithOutput 的结果：{@code unauthorized} 表示服务端因 K 失效拒绝。 */
    private static final class Reply {
        final String body;
        final boolean unauthorized;

        Reply(String body, boolean unauthorized) {
            this.body = body;
            this.unauthorized = unauthorized;
        }
    }

    /**
     * 取 K（同签名才有值）。先返回进程级缓存；为空时向 launcher provider 拉取并缓存。
     * 鉴权被拒（异签名）返回 null，后续 exec 直接失败。
     */
    private static String getKey() {
        String cached = sKey;
        if (cached != null) return cached;
        Context ctx = sAppContext;
        if (ctx == null) {
            Log.w(TAG, "getKey: 未 init(context)，无法调用 provider");
            return null;
        }
        String pkg = resolveLauncherPackage(ctx);
        if (pkg == null) return null;
        Uri uri = Uri.parse("content://" + pkg + MiniShizukuConst.AUTHORITY_SUFFIX);
        try {
            Bundle b = ctx.getContentResolver().call(uri, MiniShizukuConst.METHOD_GET_KEY, null, null);
            if (b != null) {
                sKey = b.getString(MiniShizukuConst.EXTRA_KEY);
            }
        } catch (Throwable e) {
            Log.w(TAG, "getKey failed: " + e.getMessage());
        }
        return sKey;
    }

    /**
     * 丢弃缓存的 K（与 launcher 包名缓存）并立即重新向 provider 拉取。
     * <p>
     * launcher 被卸载重装、或其进程重启都会换一把新 K（{@code KeydroidxShizukuKeyHolder}
     * 是进程级随机值），此时旧缓存会被服务端回 {@code ERR:unauthorized}——表现为
     * 「桌面重装后本应用一直提示签名不匹配」，必须丢弃重拉才能恢复。
     * <p>
     * 兜底：{@link #execAcked} / {@link #execWithOutput} 收到 unauthorized 时会自行调用本
     * 方法并重试一次；调用方也可主动调用（如状态诊断前强制刷新）以避免误报。
     *
     * @return 重拉后的 K；launcher 缺失或异签名时为 {@code null}
     */
    public static String refreshKey() {
        invalidateKey();
        return getKey();
    }

    /** 仅丢弃进程内缓存（K + launcher 包名），下次执行时才重拉。 */
    public static void invalidateKey() {
        sKey = null;
        sLauncherPackage = null;
    }

    /**
     * 解析本机已安装的 launcher 包名（正式/调试），并校验其签名与本进程一致
     * （防假冒：异签名应用冒名声明 provider 也不认）。结果缓存。
     */
    private static String resolveLauncherPackage(Context ctx) {
        if (sLauncherPackage != null) return sLauncherPackage;
        // 1. 优先：若调用者自身就是 launcher 候选包名（无论 debug/release 变体），直接使用自身
        String selfPkg = ctx.getPackageName();
        for (String pkg : MiniShizukuConst.LAUNCHER_PACKAGES) {
            if (pkg.equals(selfPkg)) {
                return sLauncherPackage = selfPkg;
            }
        }

        // 2. 第三方应用调用：解析同签名且可用的 launcher 包名
        byte[] selfSig = selfSignatureDigest(ctx);
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : MiniShizukuConst.LAUNCHER_PACKAGES) {
            try {
                PackageInfo info = pm.getPackageInfo(pkg,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? PackageManager.GET_SIGNING_CERTIFICATES
                                : PackageManager.GET_SIGNATURES);
                // 忽略未启用/已冻结的包
                if (info.applicationInfo != null && !info.applicationInfo.enabled) {
                    continue;
                }
                // 确保 provider 实际存在且已导出
                if (pm.resolveContentProvider(pkg + MiniShizukuConst.AUTHORITY_SUFFIX, 0) == null) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
                    for (Signature s : info.signingInfo.getApkContentsSigners()) {
                        if (digestEquals(s.toByteArray(), selfSig)) {
                            return sLauncherPackage = pkg;
                        }
                    }
                } else if (info.signatures != null) {
                    for (Signature s : info.signatures) {
                        if (digestEquals(s.toByteArray(), selfSig)) {
                            return sLauncherPackage = pkg;
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // 该包未安装，试下一个
            }
        }
        Log.w(TAG, "resolveLauncherPackage: 未找到同签名的 launcher");
        return null;
    }

    private static byte[] selfSignatureDigest(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
                sigs = info.signingInfo.getApkContentsSigners();
            } else {
                sigs = info.signatures;
            }
            if (sigs != null && sigs.length > 0) {
                return sha256(sigs[0].toByteArray());
            }
        } catch (Exception e) {
            Log.w(TAG, "selfSignatureDigest failed", e);
        }
        return null;
    }

    private static boolean digestEquals(byte[] sigBytes, byte[] expectedDigest) {
        if (sigBytes == null || expectedDigest == null) return false;
        return MessageDigest.isEqual(sha256(sigBytes), expectedDigest);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
