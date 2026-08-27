package io.github.cctyl.nokia.common.feedback;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 纯 Java Ed25519 签名实现（RFC 8032，仅签名不含验签）。
 *
 * <p>引入本类的目的：SDK 零第三方依赖原则下无法使用 BouncyCastle，
 * 而 Android 自带 crypto 在低版本上不提供 Ed25519，故自行实现。
 * 实现已通过 RFC 8032 §7.1 测试向量（TEST 1/2/3）验证。</p>
 *
 * <p>单次签名采用 BigInteger 大数运算，耗时约几十至几百毫秒（低端机），
 * 仅用于用户手动提交反馈的场景，性能完全够用。</p>
 */
public final class NokiaEd25519 {

    private static final BigInteger P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger L = BigInteger.valueOf(2).pow(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    /** 基点（扩展齐次坐标 [X:Y:T:Z]，T = XY/Z） */
    private static final BigInteger[] BASE = {
            new BigInteger("216936D3CD6E53FEC0A4E231FDD6DC5C692CC7609525A7B2C9562D608F25D51A", 16),
            new BigInteger("6666666666666666666666666666666666666666666666666666666666666658", 16),
            null,
            BigInteger.ONE
    };
    static {
        BASE[2] = BASE[0].multiply(BASE[1]).mod(P);
    }

    private NokiaEd25519() {
    }

    /**
     * Ed25519 签名。
     *
     * @param seed    32 字节私钥种子
     * @param message 待签名消息
     * @return 64 字节签名
     */
    public static byte[] sign(byte[] seed, byte[] message) {
        try {
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            byte[] h = sha512.digest(seed);
            byte[] aBytes = Arrays.copyOfRange(h, 0, 32);
            aBytes[0] &= (byte) 248;
            aBytes[31] &= (byte) 127;
            aBytes[31] |= (byte) 64;
            BigInteger a = littleEndian(aBytes).mod(L);
            byte[] prefix = Arrays.copyOfRange(h, 32, 64);

            byte[] rHash = sha512.digest(concat(prefix, message));
            BigInteger r = littleEndian(rHash).mod(L);
            byte[] rEnc = encode(mul(BASE, r));

            byte[] kHash = sha512.digest(concat(rEnc, concat(encode(mul(BASE, a)), message)));
            BigInteger k = littleEndian(kHash).mod(L);
            BigInteger s = r.add(k.multiply(a)).mod(L);

            byte[] sig = new byte[64];
            System.arraycopy(rEnc, 0, sig, 0, 32);
            System.arraycopy(littleEndianBytes(s), 0, sig, 32, 32);
            return sig;
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 sign failed", e);
        }
    }

    // ---------- 扩展坐标点运算 ----------

    /** 点加法（RFC 8032 统一公式） */
    private static BigInteger[] add(BigInteger[] p, BigInteger[] q) {
        BigInteger x1 = p[0], y1 = p[1], t1 = p[2], z1 = p[3];
        BigInteger x2 = q[0], y2 = q[1], t2 = q[2], z2 = q[3];
        BigInteger a = y1.subtract(x1).multiply(y2.subtract(x2)).mod(P);
        BigInteger b = y1.add(x1).multiply(y2.add(x2)).mod(P);
        BigInteger c = t1.multiply(D).multiply(t2).shiftLeft(1).mod(P); // C = 2d·T1·T2
        BigInteger d = z1.multiply(z2).shiftLeft(1).mod(P);             // D = 2·Z1·Z2
        BigInteger e = b.subtract(a).mod(P);
        BigInteger f = d.subtract(c).mod(P);
        BigInteger g = d.add(c).mod(P);
        BigInteger h = b.add(a).mod(P);
        return new BigInteger[]{
                e.multiply(f).mod(P), g.multiply(h).mod(P), e.multiply(h).mod(P), f.multiply(g).mod(P)};
    }

    /** 倍点（RFC 8032 倍点公式；注意 Z 取下标 [3]） */
    private static BigInteger[] dbl(BigInteger[] p) {
        BigInteger x = p[0], y = p[1], z = p[3];
        BigInteger a = x.multiply(x).mod(P);
        BigInteger b = y.multiply(y).mod(P);
        BigInteger c = z.multiply(z).shiftLeft(1).mod(P);
        BigInteger h = a.add(b).mod(P);
        BigInteger e = h.subtract(x.add(y).mod(P).pow(2)).mod(P);
        BigInteger g = a.subtract(b).mod(P);
        BigInteger f = c.add(g).mod(P);
        return new BigInteger[]{
                e.multiply(f).mod(P), g.multiply(h).mod(P), e.multiply(h).mod(P), f.multiply(g).mod(P)};
    }

    /** 简单 double-and-add 标量乘 */
    private static BigInteger[] mul(BigInteger[] point, BigInteger k) {
        BigInteger[] r = {BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE};
        BigInteger[] base = point;
        while (k.signum() > 0) {
            if (k.testBit(0)) {
                r = add(r, base);
            }
            base = dbl(base);
            k = k.shiftRight(1);
        }
        return r;
    }

    /** 点编码：y 的 32 字节小端 + 最高位为 x 的符号位 */
    private static byte[] encode(BigInteger[] p) {
        BigInteger x = p[0].multiply(p[3].modInverse(P)).mod(P);
        BigInteger y = p[1].multiply(p[3].modInverse(P)).mod(P);
        byte[] le = new byte[32];
        for (int i = 0; i < 32; i++) {
            le[i] = y.and(BigInteger.valueOf(0xFF)).byteValue();
            y = y.shiftRight(8);
        }
        if (x.testBit(0)) {
            le[31] |= (byte) 0x80;
        }
        return le;
    }

    // ---------- 字节工具 ----------

    private static BigInteger littleEndian(byte[] b) {
        BigInteger v = BigInteger.ZERO;
        for (int i = b.length - 1; i >= 0; i--) {
            v = v.shiftLeft(8).or(BigInteger.valueOf(b[i] & 0xFF));
        }
        return v;
    }

    private static byte[] littleEndianBytes(BigInteger v) {
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = v.and(BigInteger.valueOf(0xFF)).byteValue();
            v = v.shiftRight(8);
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] o = new byte[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }
}
