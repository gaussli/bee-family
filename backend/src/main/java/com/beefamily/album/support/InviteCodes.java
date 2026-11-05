package com.beefamily.album.support;

import java.security.SecureRandom;

/**
 * 邀请码生成器。
 *
 * <p>字符集刻意剔除易混字符 {@code 0/O/1/I/L}：邀请码需要家人口述或手抄，
 * 剔除后误读率显著下降（docs T05 关键约束④）。使用 {@link SecureRandom} 避免可预测序列。</p>
 */
public final class InviteCodes {

    /** 去混淆字符集：26 字母 + 9 数字 - (O, I, L, 0, 1)。 */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
        // 工具类禁止实例化
    }

    /**
     * 生成指定长度的邀请码。
     *
     * @param length 长度
     * @return 邀请码（大写字母 + 数字）
     */
    public static String generate(int length) {
        int safeLength = Math.max(length, Limits.INVITE_CODE_LENGTH);
        char[] chars = new char[safeLength];
        for (int i = 0; i < safeLength; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    /**
     * 生成默认长度（8 位）的邀请码。
     *
     * @return 邀请码
     */
    public static String generate() {
        return generate(Limits.INVITE_CODE_LENGTH);
    }
}
