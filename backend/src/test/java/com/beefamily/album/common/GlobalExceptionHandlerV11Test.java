package com.beefamily.album.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 全局异常出口的 HTTP 状态映射（docs/07 §3.4、附录 B）。
 *
 * <p>重点验证 v1.1 新增的 429 / 503 不能退化成 500，以及 40304 / 40305 保持 403
 * （前端 {@code ApiError.isAuthError} 只认 40100..40199，误降级会导致用户被踢回登录页）。</p>
 */
class GlobalExceptionHandlerV11Test {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 提供「错误码 → 期望 HTTP 状态」的用例。
     *
     * @return 参数流
     */
    private static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of(ErrorCode.HEIC_TRANSCODER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(ErrorCode.NO_ACTIVE_FAMILY, HttpStatus.FORBIDDEN),
                Arguments.of(ErrorCode.ADMIN_ONLY, HttpStatus.FORBIDDEN),
                Arguments.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                Arguments.of(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE),
                Arguments.of(ErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST),
                Arguments.of(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
                Arguments.of(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED),
                Arguments.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT),
                Arguments.of(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of(ErrorCode.STORAGE_WRITE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of(ErrorCode.HEIC_TRANSCODE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("statusCases")
    @DisplayName("业务异常按错误码映射为正确的 HTTP 状态，响应体带同一错误码")
    void mapsBusinessExceptionToHttpStatus(ErrorCode errorCode, HttpStatus expected) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(new BusinessException(errorCode));
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(errorCode.getCode());
    }
}
