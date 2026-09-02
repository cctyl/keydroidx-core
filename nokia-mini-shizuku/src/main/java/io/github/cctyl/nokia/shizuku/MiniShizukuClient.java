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
 * K 的获取：调用 launcher 的 {@code NokiaShizukuProvider.call("getKey")}。provider 按
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
    private static String sKey;
    private static Context sAppContext;

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
     */
    public static boolean execAcked(String command) {
        String k = getKey();
        if (k == null) return false;
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
                return line != null && !line.startsWith("ERR:");
            } catch (java.net.SocketTimeoutException e) {
                // 老服务端不回 ack：写入成功即视为成功
                return true;
            }
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * 执行并回读输出，直到 {@code EXIT:<code>}。
     * 鉴权失败（server 回 {@code ERR:unauthorized}）返回 null。
     */
    public static String execWithOutput(String command) {
        String k = getKey();
        if (k == null) return null;
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
                return null; // 鉴权失败
            }
            StringBuilder sb = new StringBuilder();
            if (first != null) {
                if (first.startsWith(MiniShizukuConst.EXIT_PREFIX)) {
                    return ""; // 无输出，直接结束
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
            return sb.toString();
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * 取 K（同签名才有值）。先返回进程级缓存；为空时向 launcher provider 拉取并缓存。
     * 鉴权被拒（异签名）返回 null，后续 exec 直接失败。
     */
    private static String getKey() {
        if (sKey != null) return sKey;
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
        } catch (SecurityException e) {
            Log.w(TAG, "getKey denied: " + e.getMessage());
        }
        return sKey;
    }

    /**
     * 解析本机已安装的 launcher 包名（正式/调试），并校验其签名与本进程一致
     * （防假冒：异签名应用冒名声明 provider 也不认）。结果缓存。
     */
    private static String resolveLauncherPackage(Context ctx) {
        if (sLauncherPackage != null) return sLauncherPackage;
        byte[] selfSig = selfSignatureDigest(ctx);
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : MiniShizukuConst.LAUNCHER_PACKAGES) {
            try {
                PackageInfo info = pm.getPackageInfo(pkg,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? PackageManager.GET_SIGNING_CERTIFICATES
                                : PackageManager.GET_SIGNATURES);
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
