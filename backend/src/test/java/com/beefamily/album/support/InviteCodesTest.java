package com.beefamily.album.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * {@link InviteCodes} 单元测试：
 * 覆盖任务点 6（8 位、去混淆字符集、24h 有效期）。
 */
class InviteCodesTest {

    /**
     * 去混淆字符集：剔除 0 / O / 1 / I / L 后的 26 字母 + 9 数字。
     */
    private static final String ALLOWED_PATTERN = "[A-HJ-KM-NP-Z2-9]{8}";

    @RepeatedTest(50)
    @DisplayName("邀请码恒为 8 位，且只含去混淆字符集中的字符")
    void codeFormat() {
        String code = InviteCodes.generate();
        assertThat(code).hasSize(Limits.INVITE_CODE_LENGTH).matches(ALLOWED_PATTERN);
    }

    @Test
    @DisplayName("字符集不含易混淆字符 0 / O / 1 / I / L")
    void noConfusableCharacters() {
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            all.append(InviteCodes.generate());
        }
        assertThat(all.toString())
                .doesNotContain("0")
                .doesNotContain("O")
                .doesNotContain("1")
                .doesNotContain("I")
                .doesNotContain("L");
    }

    @Test
    @DisplayName("500 个邀请码无重复（SecureRandom + 31^8 空间）")
    void codesAreDistinct() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(InviteCodes.generate());
        }
        assertThat(codes).hasSize(500);
    }

    @Test
    @DisplayName("请求长度小于 8 时被抬升到 8（不会生成过短邀请码）")
    void shortLengthIsClamped() {
        assertThat(InviteCodes.generate(4)).hasSize(8).matches(ALLOWED_PATTERN);
        assertThat(InviteCodes.generate(0)).hasSize(8);
        assertThat(InviteCodes.generate(-3)).hasSize(8);
    }

    @Test
    @DisplayName("显式更长长度按请求返回")
    void longerLength() {
        assertThat(InviteCodes.generate(12)).hasSize(12).matches("[A-HJ-KM-NP-Z2-9]{12}");
    }

    @Test
    @DisplayName("有效期常量为 24 小时")
    void ttlIs24Hours() {
        assertThat(Limits.INVITE_CODE_TTL).isEqualTo(Duration.ofHours(24));
    }
}
