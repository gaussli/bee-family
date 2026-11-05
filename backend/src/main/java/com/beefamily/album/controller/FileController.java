package com.beefamily.album.controller;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.service.StorageService;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件读取接口。
 *
 * <p><b>凭证双通道</b>：本路径同时接受 {@code Authorization: Bearer} 与 {@code ?token=}（ADR-04），
 * 因为 {@code <img src>} 无法设置请求头。token 出现在 URL 的缓解措施：Referrer-Policy: no-referrer、
 * 访问日志脱敏、有效期 7 天。</p>
 *
 * <p><b>两道校验</b>：① key 首段必须是当前家庭 id（横向越权）；② {@link StorageService#resolve} 做
 * {@code normalize + startsWith(root)}（路径穿越）。</p>
 *
 * <p>返回 {@code ResponseEntity<Resource>}，Spring MVC 自动处理 {@code Range} 与 {@code Last-Modified}。</p>
 */
@RestController
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /** 缓存 30 天：照片写入后不可变，可安全长缓存。 */
    private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(30))
            .cachePrivate()
            .immutable();

    private final StorageService storageService;
    private final FamilyAccessGuard guard;

    /**
     * 构造控制器。
     *
     * @param storageService 存储服务
     * @param guard          家庭闸门
     */
    public FileController(StorageService storageService, FamilyAccessGuard guard) {
        this.storageService = storageService;
        this.guard = guard;
    }

    /**
     * 读取图片。
     *
     * @param key {@code /api/files/} 之后的完整相对路径（含前导 /）
     * @return 二进制流
     */
    @GetMapping("/api/files/{*key}")
    public ResponseEntity<Resource> read(@PathVariable String key) {
        String relativeKey = normalize(key);
        Long familyId = guard.familyId();
        // 横向越权：key 的第一段就是 familyId，路径片段无用户输入，无法构造出别人的目录
        if (!relativeKey.startsWith(familyId + "/")) {
            log.warn("cross-family file access blocked familyId={} key={}", familyId, relativeKey);
            throw new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS);
        }
        Resource resource = storageService.loadAsResource(relativeKey);
        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(resolveMediaType(relativeKey))
                .contentLength(contentLength)
                .cacheControl(CACHE_CONTROL)
                .body(resource);
    }

    /**
     * 去掉 {@code {*key}} 自带的前导斜杠。
     *
     * @param key 原始路径变量
     * @return 相对 key
     */
    private String normalize(String key) {
        if (key == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String value = key;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    /**
     * 按扩展名推断 Content-Type。
     *
     * @param key 相对 key
     * @return 媒体类型
     */
    private MediaType resolveMediaType(String key) {
        String extension = key.toLowerCase(Locale.ROOT);
        if (extension.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (extension.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
