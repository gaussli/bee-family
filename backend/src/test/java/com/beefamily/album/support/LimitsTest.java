package com.beefamily.album.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link Limits} 阈值与固定文案测试（任务点 4、5）。
 *
 * <p>这些常量被 PRD 验收标准直接引用，任何一次手滑改动都等于悄悄改需求，
 * 因此用断言把它们钉死。</p>
 */
class LimitsTest {

    @Test
    @DisplayName("体积 / 批量 / 标签 / 像素阈值与 PRD 一致")
    void thresholdsMatchPrd() {
        assertThat(Limits.MAX_FILE_SIZE_BYTES).isEqualTo(20L * 1024 * 1024);
        assertThat(Limits.MAX_BATCH_SIZE).isEqualTo(100);
        assertThat(Limits.MIN_BATCH_SIZE).isEqualTo(1);
        assertThat(Limits.FAMILY_MEMBER_LIMIT).isEqualTo(6);
        assertThat(Limits.BABY_LIMIT).isEqualTo(3);
        assertThat(Limits.TAG_NAME_MAX_LENGTH).isEqualTo(20);
        assertThat(Limits.TAG_LIST_LIMIT).isEqualTo(20);
        assertThat(Limits.MAX_IMAGE_PIXELS).isEqualTo(100_000_000L);
        assertThat(Limits.INVITE_CODE_TTL).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("缩略图 480 / 预览 1600，质量档位固定")
    void imageSizes() {
        assertThat(Limits.THUMB_MAX_EDGE).isEqualTo(480);
        assertThat(Limits.PREVIEW_MAX_EDGE).isEqualTo(1600);
        assertThat(Limits.THUMB_QUALITY).isBetween(0.7f, 0.9f);
        assertThat(Limits.PREVIEW_QUALITY).isBetween(Limits.THUMB_QUALITY, 1.0f);
    }

    @Test
    @DisplayName("HEIC 文案必须同时含「iPhone」与「兼容性最佳」（ADR-01 的可用性要求）")
    void heicHintIsActionable() {
        assertThat(Limits.HEIC_HINT).contains("iPhone").contains("兼容性最佳");
    }

    @Test
    @DisplayName("通用不支持文案列出全部白名单后缀")
    void unsupportedHintListsWhitelist() {
        for (String extension : Limits.ALLOWED_EXTENSIONS) {
            assertThat(Limits.UNSUPPORTED_HINT).contains(extension);
        }
    }

    @Test
    @DisplayName("白名单只含 jpg/jpeg/png/webp")
    void allowedExtensions() {
        assertThat(Limits.ALLOWED_EXTENSIONS).containsExactly("jpg", "jpeg", "png", "webp");
    }

    @Test
    @DisplayName("白名单与黑名单无交集（否则合法格式会被自己拦掉）")
    void allowedAndBlockedAreDisjoint() {
        List<String> overlap = Limits.ALLOWED_EXTENSIONS.stream()
                .filter(Limits.BLOCKED_EXTENSIONS::contains)
                .toList();
        assertThat(overlap).isEmpty();
    }

    @ParameterizedTest(name = "{0} 在拒绝清单内")
    @ValueSource(strings = {"svg", "svgz", "heic", "heif", "exe", "sh", "bat", "js", "html", "php",
            "jar", "zip", "pdf", "gif", "bmp", "tif", "ico", "psd", "mov", "mp4"})
    @DisplayName("拒绝清单覆盖可执行 / 可内嵌脚本 / 不支持的图片容器")
    void blockedExtensions(String extension) {
        assertThat(Limits.BLOCKED_EXTENSIONS).contains(extension);
    }
}
