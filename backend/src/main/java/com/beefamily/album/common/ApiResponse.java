package com.beefamily.album.common;

/**
 * 统一响应体。
 *
 * <p>成功恒为 {@code code = 0}；错误响应中 {@code data} 恒为 {@code null}，{@code message} 不含任何
 * 内部类名 / SQL / 文件绝对路径（见 docs/03-architecture.md §3.3、§3.4）。</p>
 *
 * @param code    业务码，0 表示成功
 * @param message 提示文案，成功为 "OK"
 * @param data    业务数据，可为 null
 * @param traceId 16 位十六进制链路标识
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    /** 成功文案。 */
    public static final String OK_MESSAGE = "OK";

    /**
     * 成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), OK_MESSAGE, data, TraceIds.current());
    }

    /**
     * 成功响应（无数据）。
     *
     * @return 统一响应体，{@code data} 为 null
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.getCode(), OK_MESSAGE, null, TraceIds.current());
    }

    /**
     * 失败响应（使用错误码默认文案）。
     *
     * @param errorCode 错误码
     * @param <T>       数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getDefaultMessage());
    }

    /**
     * 失败响应（自定义文案）。
     *
     * @param errorCode 错误码
     * @param message   文案，为空时回退到错误码默认文案
     * @param <T>       数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        String text = (message == null || message.isBlank()) ? errorCode.getDefaultMessage() : message;
        return new ApiResponse<>(errorCode.getCode(), text, null, TraceIds.current());
    }
}
