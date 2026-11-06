package com.beefamily.album.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常出口。
 *
 * <p>职责：把各类异常映射为「HTTP 状态 + 业务码 + 中文文案」的唯一组合（docs/03-architecture.md §3.4）。
 * 4xx 只记 WARN 摘要（不打请求体，避免口令/token 落日志）；5xx 记完整堆栈并带 traceId。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 字段级错误最多回显条数，超出追加「…等 N 项」。 */
    private static final int MAX_FIELD_ERRORS = 10;

    /**
     * 业务异常。
     *
     * @param ex 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getHttpStatus() >= 500) {
            log.error("business error traceId={} code={}", TraceIds.current(), errorCode.getCode(), ex);
        } else {
            log.warn("business error traceId={} code={} message={}", TraceIds.current(), errorCode.getCode(),
                    ex.getMessage());
        }
        return build(errorCode, ex.getMessage());
    }

    /**
     * 请求体字段校验失败（{@code @Valid}）。
     *
     * <p>聚合所有字段错误为一行：「nickname: 不能为空; email: 邮箱格式不正确」，最多 10 条。</p>
     *
     * @param ex 校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> parts = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            parts.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        return badRequest(parts);
    }

    /**
     * 表单/查询对象绑定校验失败。
     *
     * @param ex 绑定异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<String> parts = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            parts.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        return badRequest(parts);
    }

    /**
     * 单参数校验失败（{@code @RequestParam} 上的约束注解）。
     *
     * @param ex 校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> parts = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            parts.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        return badRequest(parts);
    }

    /**
     * 参数类型不匹配（例如 {@code range=abc}）。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("type mismatch traceId={} param={}", TraceIds.current(), ex.getName());
        return build(ErrorCode.INVALID_ARGUMENT, "参数 " + ex.getName() + " 取值非法");
    }

    /**
     * 请求体不可解析。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable body traceId={}", TraceIds.current());
        return build(ErrorCode.INVALID_ARGUMENT, "请求体格式错误");
    }

    /**
     * 缺少必要查询参数。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.INVALID_PARAM, "缺少必要参数 " + ex.getParameterName());
    }

    /**
     * 单文件超过 multipart 上限。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("upload too large traceId={}", TraceIds.current());
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage());
    }

    /**
     * 其他 multipart 解析异常（边界损坏等）。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        log.warn("multipart error traceId={}", TraceIds.current(), ex);
        return build(ErrorCode.INVALID_ARGUMENT, "上传请求格式错误");
    }

    /**
     * 静态资源 / 路径未匹配。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage());
    }

    /**
     * 方法不被支持。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.INVALID_ARGUMENT, "不支持的请求方法 " + ex.getMethod());
    }

    /**
     * 方法级鉴权失败（正常情况下由 AccessDeniedHandler 处理，这里作为兜底）。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    /**
     * 兜底异常：日志打完整堆栈 + traceId，响应体不带任何内部细节。
     *
     * @param ex 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error traceId={}", TraceIds.current(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    /**
     * 拼装 400 字段校验错误响应。
     *
     * @param parts 字段错误片段
     * @return 统一错误响应
     */
    private ResponseEntity<ApiResponse<Void>> badRequest(List<String> parts) {
        String message;
        if (parts.isEmpty()) {
            message = ErrorCode.INVALID_PARAM.getDefaultMessage();
        } else if (parts.size() <= MAX_FIELD_ERRORS) {
            message = String.join("; ", parts);
        } else {
            message = String.join("; ", parts.subList(0, MAX_FIELD_ERRORS)) + " …等 " + parts.size() + " 项";
        }
        log.warn("validation failed traceId={} detail={}", TraceIds.current(), message);
        return build(ErrorCode.INVALID_PARAM, message);
    }

    /**
     * 按错误码构建响应。
     *
     * @param errorCode 错误码
     * @param message   文案
     * @return 统一错误响应
     */
    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(resolveStatus(errorCode)).body(ApiResponse.fail(errorCode, message));
    }

    /**
     * 业务码 → HTTP 状态（v1.1）。
     *
     * <p><b>429 / 503</b>：新增的 {@link ErrorCode#RATE_LIMITED}(42901) 与
     * {@link ErrorCode#HEIC_TRANSCODER_UNAVAILABLE}(50301) 必须经 {@link HttpStatus#resolve(int)}
     * 映射为 {@code TOO_MANY_REQUESTS} / {@code SERVICE_UNAVAILABLE}，不能退化成 500。
     * 这里刻意走统一的 resolve 而不是硬编码分支：错误码编号规则保证
     * {@code getHttpStatus()} 恒等于 HTTP 语义分组，新增码无需再改本方法。</p>
     *
     * <p><b>40304 / 40305 不误清 token</b>：前端 {@code ApiError.isAuthError} 只把
     * {@code 40100..40199} 视为登录失效并清除本地 token。这两个新码是「无可用家庭」与
     * 「仅 owner/admin」的业务态，属于 403 分组，既不能被降级成 401，也不得在这里被改写成
     * 任何 401xx；保持 403 原样即可保证用户不会被误踢回登录页。</p>
     *
     * @param errorCode 错误码
     * @return 解析后的 HTTP 状态；无法解析时兜底 500
     */
    private HttpStatus resolveStatus(ErrorCode errorCode) {
        HttpStatus status = HttpStatus.resolve(errorCode.getHttpStatus());
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
