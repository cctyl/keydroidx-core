package io.github.cctyl.nokia.common.feedback;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

import io.github.cctyl.nokia.common.log.NokiaLog;

/**
 * 安装统计上报客户端实现（纯 Java 1.8，零第三方依赖）。
 *
 * <p>与 {@link FeedbackUploader} 共用同一台服务器、同一套 HMAC-SHA256 鉴权，
 * 仅接口路径与请求体不同：</p>
 * <ul>
 *   <li>方法：POST /install</li>
 *   <li>头部鉴权：X-Timestamp, X-Nonce, X-AccessKey（算法与 /upload 完全一致）</li>
 *   <li>Content-Type：application/json</li>
 *   <li>Body：JSON 对象（设备信息字段，服务端按白名单校验）</li>
 *   <li>应答：HTTP 200 表示成功；其余一律视为失败</li>
 * </ul>
 *
 * <p>签名、hex 编解码直接复用 {@link FeedbackUploader} 的 package-private 静态方法，
 * 不重复实现，确保两端算法永远一致。</p>
 *
 * <p>协议规范见 log_upload/docs/CLIENT_API.md 第 3 节。</p>
 */
public final class InstallUploader {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String TAG = "InstallUploader";

    /** 服务端 Body 硬上限（读 Body 前超过即掐断） */
    private static final int MAX_BODY = 2048;
    /** 失败最多重试次数（含首次），间隔约 1 秒 */
    private static final int MAX_ATTEMPTS = 2;

    private InstallUploader() {
    }

    /**
     * 发送安装统计上报。
     *
     * <p>服务端失败时一律直接掐断 TCP、不返回任何错误信息，因此客户端无法区分
     * 「参数不合法被封禁」和「网络不通」。<b>最多重试 1 次</b>：若参数真有问题，
     * 无限重试只会让本机出口 IP 被持续封禁。</p>
     *
     * @param url          安装统计接口地址（…/install）
     * @param secretKeyHex 通信密钥十六进制字符串（与 /upload 共用）
     * @param json         已组装好的 JSON 字符串
     * @return true 表示服务端确认成功（HTTP 200）
     */
    public static boolean send(String url, String secretKeyHex, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY) {
            NokiaLog.w(TAG, "install body too large: " + body.length);
            return false;
        }
        byte[] secretKey;
        try {
            secretKey = FeedbackUploader.hexToBytes(secretKeyHex);
        } catch (Throwable t) {
            NokiaLog.w(TAG, "secret key invalid: " + t.getMessage());
            return false;
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (doSend(url, secretKey, body)) {
                return true;
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean doSend(String urlStr, byte[] secretKey, byte[] body) {
        HttpURLConnection conn = null;
        try {
            long timestamp = System.currentTimeMillis();
            byte[] nonceBytes = new byte[16];
            new SecureRandom().nextBytes(nonceBytes);
            String nonce = FeedbackUploader.bytesToHex(nonceBytes);
            String accessKey = FeedbackUploader.computeAccessKey(secretKey, timestamp, nonce);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
            conn.setRequestProperty("X-Nonce", nonce);
            conn.setRequestProperty("X-AccessKey", accessKey);
            conn.setFixedLengthStreamingMode(body.length);

            OutputStream out = conn.getOutputStream();
            out.write(body);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                NokiaLog.w(TAG, "server response code: " + code);
            }
            return code == 200;
        } catch (Throwable t) {
            NokiaLog.w(TAG, "send failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- JSON 组装 ----------

    /**
     * 组装符合 /install 白名单的 JSON。
     *
     * <p>服务端按白名单校验：白名单外字段会被忽略；白名单内字段类型/长度/取值范围
     * 不对 → 判定为协议违规并封禁 IP 1 小时。本方法严格只写入协议规定的字段，
     * 并对每个值做长度截断与类型归一，确保不触发封禁。</p>
     *
     * <p>必填 7 个全部写入；选填 11 个从 extras 中按白名单键名挑取，能取到才带。</p>
     *
     * @param appName    应用标识（仅 [A-Za-z0-9_-]，≤32）
     * @param appVersion 应用版本名（≤32）
     * @param androidId  ANDROID_ID（8-64 位十六进制，已校验非空/非全零）
     * @param extras     {@link DeviceInfoCollector#collect} 返回的设备信息 map
     */
    public static String buildInstallJson(String appName, String appVersion,
                                          String androidId, Map<String, Object> extras)
            throws org.json.JSONException {
        JSONObject j = new JSONObject();
        // ---- 必填 7 个 ----
        j.put("app", take(appName, 32));
        j.put("app_version", take(appVersion, 32));
        j.put("android_id", take(androidId, 64));
        j.put("device_brand", take(stringFrom(extras, "device_brand"), 64));
        j.put("device_model", take(stringFrom(extras, "device_model"), 64));
        j.put("android_version", take(stringFrom(extras, "android_version"), 16));
        j.put("android_api", intFrom(extras, "android_api", 0));
        // ---- 选填 11 个（能取到就带，取不到不写） ----
        putOptionalString(j, "device_manufacturer", stringFrom(extras, "device_manufacturer"), 64);
        putOptionalString(j, "locale", stringFrom(extras, "locale"), 32);
        putOptionalString(j, "screen_px", stringFrom(extras, "screen_px"), 32);
        putOptionalString(j, "screen_density", stringFrom(extras, "screen_density"), 16);
        putOptionalInt(j, "total_mem_mb", intFrom(extras, "total_mem_mb", -1), 0, 1_000_000);
        putOptionalInt(j, "total_disk_mb", intFrom(extras, "total_disk_mb", -1), 0, 1_000_000_000);
        putOptionalString(j, "cpu_hardware", stringFrom(extras, "cpu_hardware"), 64);
        putOptionalInt(j, "cpu_cores", intFrom(extras, "cpu_cores", -1), 1, 256);
        putOptionalString(j, "gpu_renderer", stringFrom(extras, "gpu_renderer"), 64);
        putOptionalString(j, "gpu_vendor", stringFrom(extras, "gpu_vendor"), 32);
        putOptionalString(j, "supported_abis", stringFrom(extras, "supported_abis"), 128);
        return j.toString();
    }

    /** 仅在 value 非空时写入（截断到 max） */
    private static void putOptionalString(JSONObject j, String key, String value, int max)
            throws org.json.JSONException {
        if (value != null && !value.isEmpty()) {
            j.put(key, take(value, max));
        }
    }

    /** 仅在 value >= 0 且在 [min,max] 范围时写入 */
    private static void putOptionalInt(JSONObject j, String key, int value, int min, int max)
            throws org.json.JSONException {
        if (value >= min && value <= max) {
            j.put(key, value);
        }
    }

    /** 从 extras 取字符串值，兼容 String/Number/Boolean */
    private static String stringFrom(Map<String, Object> extras, String key) {
        if (extras == null) return null;
        Object v = extras.get(key);
        if (v == null) return null;
        return String.valueOf(v);
    }

    /** 从 extras 取 int 值，兼容 Number/可解析数字的 String */
    private static int intFrom(Map<String, Object> extras, String key, int def) {
        if (extras == null) return def;
        Object v = extras.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static String take(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
