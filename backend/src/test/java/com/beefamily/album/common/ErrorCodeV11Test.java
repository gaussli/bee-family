package com.beefamily.album.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/**
 * v1.1 错误码表（docs/07 附录 B，35 项）的验收测试。
 *
 * <p>错误码是前后端契约的一部分，编号规则、HTTP 分组、固定文案与「40902 永久废弃」
 * 都必须被钉死，防止后续改动悄悄破坏契约。</p>
 */
class ErrorCodeV11Test {

    @ParameterizedTest(name = "{0} 的编号 = HTTP 分组 × 100 + 序号")
    @EnumSource(ErrorCode.class)
    @DisplayName("35 项错误码遵循 code = httpStatus × 100 + 序号（OK 恒为 0 是唯一例外）")
    void codeMatchesHttpGroup(ErrorCode errorCode) {
        if (errorCode == ErrorCode.OK) {
            assertThat(errorCode.getCode()).isZero();
        } else {
            assertThat(errorCode.getCode() / 100).isEqualTo(errorCode.getHttpStatus());
            assertThat(errorCode.getCode() % 100).isBetween(0, 99);
        }
    }

    @ParameterizedTest(name = "{0} 的 httpStatus 可被 Spring 解析且默认文案非空")
    @EnumSource(ErrorCode.class)
    @DisplayName("每个错误码的 httpStatus 都能经 HttpStatus.resolve() 映射，且默认文案非空")
    void statusResolvableAndMessagePresent(ErrorCode errorCode) {
        assertThat(HttpStatus.resolve(errorCode.getHttpStatus())).isNotNull();
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("有效错误码恰好 35 项，且编号无重复")
    void exactly35UniqueCodes() {
        assertThat(ErrorCode.values()).hasSize(35);
        long distinct = Arrays.stream(ErrorCode.values()).mapToInt(ErrorCode::getCode).distinct().count();
        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("42901 → 429 / 50301 → 503，映射为 TOO_MANY_REQUESTS / SERVICE_UNAVAILABLE")
    void rateLimitAndServiceUnavailable() {
        assertThat(ErrorCode.RATE_LIMITED.getHttpStatus()).isEqualTo(429);
        assertThat(HttpStatus.resolve(ErrorCode.RATE_LIMITED.getHttpStatus()))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.HEIC_TRANSCODER_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
        assertThat(HttpStatus.resolve(ErrorCode.HEIC_TRANSCODER_UNAVAILABLE.getHttpStatus()))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("50003 与 50301 的 defaultMessage 逐字一致，且为 06 §9.2 ④ 权威文案")
    void heicFailureMessagesAreIdentical() {
        String authoritative = "这张 HEIC 暂时无法解析，请在 iPhone 相册中打开照片 → 分享 →『导出为 JPEG』后重新上传。"
                + "原文件不会丢失。";
        assertThat(ErrorCode.HEIC_TRANSCODE_FAILED.getDefaultMessage()).isEqualTo(authoritative);
        assertThat(ErrorCode.HEIC_TRANSCODER_UNAVAILABLE.getDefaultMessage()).isEqualTo(authoritative);
    }

    @Test
    @DisplayName("41501 默认文案固定，且把 HEIC 列入支持格式")
    void unsupportedMediaTypeMessageIsFixed() {
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage())
                .isEqualTo("不支持的图片格式（支持 JPG / PNG / WebP / HEIC）");
    }

    @Test
    @DisplayName("v1.1 新增的 11 个错误码全部就位")
    void newV11CodesPresent() {
        int[] codes = Arrays.stream(ErrorCode.values()).mapToInt(ErrorCode::getCode).toArray();
        assertThat(codes).contains(40304, 40305, 40402, 40909, 40910, 40911, 40912, 40913,
                42901, 50003, 50301);
    }

    @Test
    @DisplayName("40902 INVITE_CODE_INVALID 永久废弃，编号不再分配")
    void retired40902NeverReused() {
        assertThat(Arrays.stream(ErrorCode.values()).mapToInt(ErrorCode::getCode)).doesNotContain(40902);
    }

    @Test
    @DisplayName("40304 / 40305 属 403 分组，不会被前端 isAuthError 误判为登录失效")
    void noActiveFamilyAndAdminOnlyAre403() {
        assertThat(ErrorCode.NO_ACTIVE_FAMILY.getCode()).isBetween(40300, 40399);
        assertThat(ErrorCode.ADMIN_ONLY.getCode()).isBetween(40300, 40399);
    }

    @Test
    @DisplayName("全部默认文案不出现 06 §9.4 全站禁用措辞")
    void defaultMessagesAvoidBannedWording() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getDefaultMessage())
                    .as("%s 的默认文案", errorCode.name())
                    .doesNotContain("兼容性最佳", "不支持 HEIC", "24 小时内有效", "邀请码", "仅 macOS",
                            "GIF 支持");
        }
    }
}
