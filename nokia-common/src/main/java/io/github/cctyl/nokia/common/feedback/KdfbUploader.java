package io.github.cctyl.nokia.common.feedback;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * KDFB v1 反馈上报协议实现（纯 Java，无第三方依赖）。
 *
 * <p>报文格式（大端序）：</p>
 * <pre>
 * magic(8) | version(1) | reserved(1) | timestamp(8) | nonce(16)
 * | meta_len(2) | meta(N) | signature(64) | zip_len(4) | zip(M)
 * </pre>
 *
 * <p>签名覆盖 version 起至 zip 末尾的连续字节（不含 magic 与 signature 本身）。</p>
 */
public final class KdfbUploader {

    /** 协议魔数 "KDFB1\0\0\0" */
    private static final byte[] MAGIC = {0x4B, 0x44, 0x46, 0x42, 0x31, 0, 0, 0};
    private static final byte VERSION = 0x01;
    /** 打包目标上限（留余量，服务端硬限 10MB） */
    private static final int MAX_ZIP = 9 * 1024 * 1024;
    /** 单日志文件超过则截断 */
    private static final long MAX_SINGLE_LOG = 8L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SO_TIMEOUT_MS = 15_000;

    private KdfbUploader() {
    }

    /**
     * 组包、签名并发送。
     *
     * @return true 表示服务端确认成功（应答字节 0x01）
     * @throws IllegalArgumentException 私钥 hex 非法
     */
    public static boolean submit(String host, int port, String privateKeyHex,
                                 String metaJson, byte[] zip) {
        return send(host, port, hexToBytes(privateKeyHex),
                metaJson.getBytes(java.nio.charset.StandardCharsets.UTF_8), zip);
    }

    // ---------- 报文发送 ----------

    private static boolean send(String host, int port, byte[] privKey, byte[] meta, byte[] zip) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SO_TIMEOUT_MS);

            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            long timestamp = System.currentTimeMillis();

            // signed_data = version||reserved||timestamp||nonce||meta_len||meta||zip_len||zip
            ByteArrayOutputStream signed = new ByteArrayOutputStream(64 + meta.length + zip.length);
            signed.write(VERSION);
            signed.write(0x00);
            signed.write(beBytes(timestamp));
            signed.write(nonce);
            signed.write(beBytes((short) meta.length)); // meta_len 为 u16（2 字节）
            signed.write(meta);
            signed.write(beBytes(zip.length));
            signed.write(zip);
            byte[] signedData = signed.toByteArray();

            byte[] signature = NokiaEd25519.sign(privKey, signedData);

            // 完整报文：magic + 头部（version..meta）+ 签名（插在 meta 与 zip_len 之间）+ zip_len + zip
            OutputStream out = socket.getOutputStream();
            out.write(MAGIC);
            out.write(signedData, 0, 28 + meta.length); // 26 字节头 + 2 字节 meta_len + meta
            out.write(signature);
            out.write(beBytes(zip.length));
            out.write(zip);
            out.flush();

            // 成功判定：恰好读到 1 字节 0x01；EOF/超时/异常一律失败
            int resp = socket.getInputStream().read();
            if (resp != 0x01) {
                android.util.Log.w("KdfbUploader", "server response not 0x01: " + resp);
            }
            return resp == 0x01;
        } catch (IOException e) {
            android.util.Log.w("KdfbUploader", "submit failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------- meta JSON 组装 ----------

    /**
     * 组装 meta JSON。
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

        ZipResult(byte[] zipBytes, int includedFiles, int skippedFiles, long originalTotalBytes) {
            this.zipBytes = zipBytes;
            this.includedFiles = includedFiles;
            this.skippedFiles = skippedFiles;
            this.originalTotalBytes = originalTotalBytes;
        }

        public boolean isEmpty() { return zipBytes.length == 0; }

        /** 是否发生了裁剪（有文件被丢弃） */
        public boolean isTrimmed() { return skippedFiles > 0; }
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
        ZipOutputStream zos = new ZipOutputStream(bos);
        try {
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

    private static byte[] beBytes(short v) {
        return new byte[]{(byte) (v >> 8), (byte) v};
    }

    private static byte[] beBytes(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }

    private static byte[] beBytes(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("KDFB private key hex invalid");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("KDFB private key hex invalid");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
