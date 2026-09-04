package io.github.cctyl.nokia.common.feedback;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.cctyl.nokia.common.log.NokiaLog;

/**
 * HTTP 日志上传与意见反馈客户端实现（纯 Java 1.8，零第三方依赖）。
 *
 * <p>协议规范：</p>
 * <ul>
 *   <li>方法：POST /upload</li>
 *   <li>头部鉴权：X-Timestamp, X-Nonce, X-AccessKey (HMAC-SHA256), X-Meta (URL-encoded JSON)</li>
 *   <li>Body：Zip 日志压缩包（application/octet-stream，最大 10MB）</li>
 *   <li>应答：HTTP 200 表示成功</li>
 * </ul>
 */
public final class FeedbackUploader {

    /** 打包目标上限（留余量，服务端硬限 10MB） */
    public static final int MAX_ZIP = 9 * 1024 * 1024;
    /** 单日志文件超过则截断 */
    public static final long MAX_SINGLE_LOG = 8L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String TAG = "FeedbackUploader";

    private FeedbackUploader() {
    }

    /**
     * 收集日志并提交反馈（全量参数）。
     *
     * @param url          服务上传地址（如 "http://your.server.com:9421/upload"）
     * @param secretKeyHex 通信密钥十六进制字符串
     * @param appName      应用名称
     * @param appVersion   应用版本号
     * @param contact      联系方式（必填）
     * @param comment      问题描述（可选）
     * @param logDir       日志目录（为 null 或不存在则不附带日志）
     * @param extras       动态额外参数（设备信息等）
     * @return true 表示服务端确认成功（HTTP 200）
     */
    public static boolean submit(String url, String secretKeyHex,
                                 String appName, String appVersion,
                                 String contact, String comment,
                                 File logDir, Map<String, Object> extras) {
        try {
            byte[] zip = (logDir != null && logDir.isDirectory())
                    ? zipLogs(logDir).zipBytes
                    : new byte[0];
            String metaJson = buildMetaJson(appName, appVersion, contact, comment, extras);
            return send(url, hexToBytes(secretKeyHex), metaJson, zip);
        } catch (Throwable t) {
            NokiaLog.w(TAG, "submit error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 已有 meta JSON 与 zip 字节数组时的底层提交。
     */
    public static boolean submit(String url, String secretKeyHex, String metaJson, byte[] zip) {
        try {
            return send(url, hexToBytes(secretKeyHex), metaJson, zip != null ? zip : new byte[0]);
        } catch (Throwable t) {
            NokiaLog.w(TAG, "submit error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    // ---------- HTTP 上传 ----------

    private static boolean send(String urlStr, byte[] secretKey, String metaJson, byte[] zip) {
        HttpURLConnection conn = null;
        try {
            long timestamp = System.currentTimeMillis();
            byte[] nonceBytes = new byte[16];
            new SecureRandom().nextBytes(nonceBytes);
            String nonce = bytesToHex(nonceBytes);
            String accessKey = computeAccessKey(secretKey, timestamp, nonce);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
            conn.setRequestProperty("X-Nonce", nonce);
            conn.setRequestProperty("X-AccessKey", accessKey);
            // X-Meta 采用 URL 编码避免非 ASCII / 特殊字符破坏 HTTP Header
            conn.setRequestProperty("X-Meta", URLEncoder.encode(metaJson, "UTF-8"));
            conn.setFixedLengthStreamingMode(zip.length);

            OutputStream out = conn.getOutputStream();
            if (zip.length > 0) {
                out.write(zip);
            }
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                NokiaLog.w(TAG, "server response code: " + code);
            }
            return code == 200;
        } catch (IOException e) {
            NokiaLog.w(TAG, "send failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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

    static String computeAccessKey(byte[] secretKey, long timestamp, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] msg = (timestamp + ":" + nonce).getBytes(StandardCharsets.UTF_8);
            byte[] sign = mac.doFinal(msg);
            return bytesToHex(sign);
        } catch (Exception e) {
            throw new RuntimeException("Compute AccessKey failed", e);
        }
    }

    // ---------- meta JSON 组装 ----------

    /**
     * 组装 meta JSON 字符串。
     *
     * @param extras 动态字段区（设备信息等），值支持 Number/Boolean/String
     */
    public static String buildMetaJson(String app, String appVersion, String contact,
                                       String comment, Map<String, Object> extras)
            throws org.json.JSONException {
        JSONObject json = new JSONObject();
        json.put("app", truncate(app, 32));
        json.put("app_version", truncate(appVersion, 32));
        json.put("os_version", truncate(android.os.Build.VERSION.RELEASE, 32));
        json.put("contact", truncate(contact, 100));
        if (comment != null && comment.trim().length() > 0) {
            json.put("comment", truncate(comment, 500));
        }
        if (extras != null && !extras.isEmpty()) {
            JSONObject ex = new JSONObject();
            for (Map.Entry<String, Object> e : extras.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number || v instanceof Boolean) {
                    ex.put(truncate(e.getKey(), 64), v);
                } else {
                    ex.put(truncate(e.getKey(), 64), truncate(v == null ? "" : String.valueOf(v), 200));
                }
            }
            json.put("extras", ex);
        }
        return json.toString();
    }

    // ---------- 日志打包 ----------

    /** 打包结果统计，供 UI 提示用户日志裁剪情况 */
    public static final class ZipResult {
        /** zip 字节内容 */
        public final byte[] zipBytes;
        /** 实际打包的文件数 */
        public final int includedFiles;
        /** 因超限被丢弃的文件数（丢弃时优先保留最新） */
        public final int skippedFiles;
        /** 日志目录原始总大小（字节） */
        public final long originalTotalBytes;

        public ZipResult(byte[] zipBytes, int includedFiles, int skippedFiles, long originalTotalBytes) {
            this.zipBytes = zipBytes;
            this.includedFiles = includedFiles;
            this.skippedFiles = skippedFiles;
            this.originalTotalBytes = originalTotalBytes;
        }

        public boolean isEmpty() {
            return zipBytes.length == 0;
        }

        /** 是否发生了裁剪（有文件被丢弃） */
        public boolean isTrimmed() {
            return skippedFiles > 0;
        }
    }

    /**
     * 递归打包目录下所有文件为 zip。
     *
     * @param dir 日志目录，null 或不存在时返回空结果
     */
    public static ZipResult zipLogs(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return new ZipResult(new byte[0], 0, 0, 0);
        }
        File[] files = listFilesByTime(dir);
        long originalTotal = 0;
        for (File f : files) {
            originalTotal += f.length();
        }
        // 从最新的开始挑选，直到装不下为止
        java.util.List<File> kept = new java.util.ArrayList<>();
        int skipped = 0;
        long total = 0;
        for (int i = files.length - 1; i >= 0; i--) {
            File f = files[i];
            long size = Math.min(f.length(), MAX_SINGLE_LOG);
            if (size <= 0 || total + size > MAX_ZIP) {
                if (f.length() > 0) {
                    skipped++;
                }
                continue;
            }
            kept.add(f);
            total += size;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(total + 512, Integer.MAX_VALUE));
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(bos);
            for (File f : kept) {
                String entryName;
                try {
                    entryName = relativePath(dir, f);
                } catch (IOException e) {
                    entryName = f.getName();
                }
                zos.putNextEntry(new ZipEntry(entryName));
                copyLimited(f, zos, MAX_SINGLE_LOG);
                zos.closeEntry();
            }
            zos.close();
        } catch (IOException e) {
            return new ZipResult(new byte[0], 0, skipped, originalTotal);
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException ignored) {
                }
            }
        }
        return new ZipResult(bos.toByteArray(), kept.size(), skipped, originalTotal);
    }

    /** 列出目录下全部文件（含子目录），按 lastModified 升序（旧→新） */
    private static File[] listFilesByTime(File dir) {
        java.util.List<File> all = new java.util.ArrayList<>();
        collect(dir, all);
        File[] arr = all.toArray(new File[0]);
        java.util.Arrays.sort(arr, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        return arr;
    }

    private static void collect(File dir, java.util.List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                collect(c, out);
            } else if (c.isFile()) {
                out.add(c);
            }
        }
    }

    private static String relativePath(File root, File f) throws IOException {
        String rp = root.getCanonicalPath();
        String fp = f.getCanonicalPath();
        String rel = fp.startsWith(rp) ? fp.substring(rp.length()) : fp;
        rel = rel.replace('\\', '/');
        return rel.startsWith("/") ? rel.substring(1) : rel;
    }

    /** 拷贝文件内容到输出流，最多 limit 字节（超长日志截断） */
    private static void copyLimited(File file, OutputStream output, long limit) throws IOException {
        InputStream ins = null;
        try {
            ins = new java.io.FileInputStream(file);
            byte[] buf = new byte[64 * 1024];
            long remaining = limit;
            int n;
            while (remaining > 0 && (n = ins.read(buf, 0, (int) Math.min(buf.length, remaining))) >= 0) {
                output.write(buf, 0, n);
                remaining -= n;
            }
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ---------- 小工具 ----------

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Secret key hex invalid");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Secret key hex invalid");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
