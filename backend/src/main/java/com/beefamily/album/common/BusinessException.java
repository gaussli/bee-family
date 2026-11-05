package com.beefamily.album.common;

/**
 * 业务异常：service 层表达可预期业务错误的唯一方式。
 *
 * <p>controller 不捕获、不包装；由 {@link GlobalExceptionHandler} 统一转为 {@link ApiResponse}。
 * HTTP 状态码由 {@link ErrorCode#getHttpStatus()} 决定，避免在业务代码里出现魔法数字。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    /**
     * 使用错误码默认文案。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义文案（不覆盖默认文案的场景请用单参构造）。
     *
     * @param errorCode 错误码
     * @param message   面向用户的文案，禁止包含内部类名 / SQL / 绝对路径
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 错误码。
     *
     * @return 错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
