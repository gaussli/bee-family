package com.beefamily.album.common;

import java.security.SecureRandom;
import org.slf4j.MDC;

/**
 * traceId 工具：生成、读取、MDC key 常量。
 *
 * <p>traceId 由 {@link TraceIdFilter} 写入 MDC 与响应头 {@code X-Trace-Id}，格式固定 16 位十六进制，
 * 便于用户在错误 Toast 中复制后与日志对齐（docs/03-architecture.md §3.3）。</p>
 */
public final class TraceIds {

    /** MDC 中的 key。 */
    public static final String MDC_KEY = "traceId";

    /** 响应头名称。 */
    public static final String HEADER = "X-Trace-Id";

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {
        // 工具类禁止实例化
    }

    /**
     * 生成 16 位十六进制 traceId。
     *
     * @return traceId
     */
    public static String generate() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        char[] chars = new char[16];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 读取当前请求的 traceId。
     *
     * <p>理论上一定存在（过滤器最外层写入），但异步线程 / 非 Web 上下文下 MDC 可能为空，
     * 此时即时生成一个以满足「traceId 恒为 16 位 hex」的契约，而不是返回 null。</p>
     *
     * @return traceId
     */
    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return (traceId == null || traceId.isBlank()) ? generate() : traceId;
    }
}
