package com.beefamily.album.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 存储配置（{@code app.storage.*}）。
 *
 * <p>上传目录刻意放在应用工作目录之外（可配置），不通过静态资源映射暴露：
 * 文件只能经 {@code GET /api/files/**} 鉴权访问（§7.6「存储隔离」）。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 上传根目录，默认 {@code ./data/uploads}（相对启动目录）。 */
    @NotBlank
    private String root = "./data/uploads";
}
