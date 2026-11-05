package com.beefamily.album.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置（{@code app.jwt.*}）。
 *
 * <p>{@code secret} 无默认值兜底为非生产随机串（见 application.yml），启动时校验长度是否 ≥ 32 字节，
 * 不足直接失败 —— 静默使用弱密钥是比启动失败更糟的故障模式（§7.1）。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 密钥，要求 >= 32 字节。 */
    @NotBlank
    private String secret;

    /** 有效期，默认 7 天。 */
    private Duration ttl = Duration.ofDays(7);
}
