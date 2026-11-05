package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.support.ImageType;
import com.beefamily.album.support.Limits;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 缩略图服务：一张原图生成 480px 缩略图与 1600px 预览图。
 *
 * <p><b>为什么用 Thumbnailator</b>：纯 Java、两行完成等比缩放 + 质量控制，且输出基于
 * {@code BufferedImage}，天然丢弃原图 EXIF/GPS（缩略图不需要元数据，也避免隐私泄漏）。</p>
 *
 * <p><b>为什么不放大</b>：{@code scale = min(1, 目标/最长边)}，小图保持原尺寸，避免「480px 的图被拉到
 * 480 反而更模糊、体积变大」。</p>
 *
 * <p><b>PNG 保留 png</b>：相册网格里 PNG 常带透明通道，转 JPEG 会出现黑边（§8.2）。</p>
 *
 * <p><b>图片炸弹防护</b>：生成前先读像素数，超过 1 亿直接拒绝，避免解码阶段耗尽堆内存。</p>
 */
@Service
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    /** 缩略图 key 后缀。 */
    public static final String THUMB_SUFFIX = "_thumb";

    /** 预览图 key 后缀。 */
    public static final String PREVIEW_SUFFIX = "_preview";

    private final StorageService storageService;

    /**
     * 构造服务。
     *
     * @param storageService 存储服务
     */
    public ThumbnailService(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 生成两档派生图。
     *
     * <p>返回 null key 表示当前运行环境无法解码/编码该格式（例如没有 WebP ImageIO 插件），
     * 调用方应回退为「直接使用原图 key」，保证功能可用（详见 PhotoService 的上传编排）。</p>
     *
     * @param originalKey 原图 key
     * @param keyBase     去掉扩展名的 key 前缀
     * @param type        真实图片类型
     * @return 派生结果
     */
    public ThumbnailResult generate(String originalKey, String keyBase, ImageType type) {
        Path originalPath = storageService.resolve(originalKey);
        BufferedImage image;
        try {
            image = ImageIO.read(originalPath.toFile());
        } catch (Exception ex) {
            log.warn("decode image failed key={} error={}", originalKey, ex.getClass().getSimpleName());
            return ThumbnailResult.unsupported();
        }
        if (image == null) {
            // ImageIO 无对应 reader（典型：WebP 无插件）
            log.info("no image reader for key={} type={}", originalKey, type);
            return ThumbnailResult.unsupported();
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long pixels = (long) width * height;
        if (pixels > Limits.MAX_IMAGE_PIXELS) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "图片分辨率过大，无法处理");
        }

        String format = resolveOutputFormat(type);
        String thumbKey = keyBase + THUMB_SUFFIX + "." + format;
        String previewKey = keyBase + PREVIEW_SUFFIX + "." + format;
        try {
            writeDerivative(image, width, height, Limits.THUMB_MAX_EDGE, Limits.THUMB_QUALITY, format, thumbKey);
            writeDerivative(image, width, height, Limits.PREVIEW_MAX_EDGE, Limits.PREVIEW_QUALITY, format,
                    previewKey);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("generate thumbnail failed key={} error={}", originalKey, ex.getClass().getSimpleName());
            return ThumbnailResult.unsupported();
        }
        return new ThumbnailResult(width, height, thumbKey, previewKey);
    }

    /**
     * 写出一档派生图。
     *
     * @param image    原图
     * @param width    原宽
     * @param height   原高
     * @param maxEdge  最长边上限
     * @param quality  质量
     * @param format   输出格式
     * @param key      目标 key
     * @throws Exception 写出失败
     */
    private void writeDerivative(BufferedImage image, int width, int height, int maxEdge, float quality,
                                 String format, String key) throws Exception {
        double scale = Math.min(1.0d, (double) maxEdge / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        Path targetPath = storageService.resolve(key);
        java.nio.file.Files.createDirectories(targetPath.getParent());
        Thumbnails.of(image)
                .size(targetWidth, targetHeight)
                .outputFormat(format)
                .outputQuality(quality)
                .toFile(targetPath.toFile());
    }

    /**
     * 输出格式：JPEG / WebP 输出 jpg 或 webp，PNG 保持 png。
     *
     * <p>JPEG 统一用 {@code jpg} 后缀（上传的原图也可能是 {@code .jpeg}，但派生图统一为 jpg，
     * 减少前端拼接后缀的诱惑 —— 前端本就不应拼后缀，只能用 DB 返回的 key）。</p>
     *
     * @param type 真实类型
     * @return 输出格式
     */
    private String resolveOutputFormat(ImageType type) {
        return switch (type) {
            case PNG -> "png";
            case WEBP -> "webp";
            default -> "jpg";
        };
    }

    /**
     * 派生结果。
     *
     * @param width    原图宽
     * @param height   原图高
     * @param thumbKey 缩略图 key（无法生成时为 null）
     * @param previewKey 预览图 key（无法生成时为 null）
     */
    public record ThumbnailResult(int width, int height, String thumbKey, String previewKey) {

        /**
         * 不支持的格式（无 reader / writer）。
         *
         * @return 结果
         */
        public static ThumbnailResult unsupported() {
            return new ThumbnailResult(0, 0, null, null);
        }

        /**
         * 是否成功生成两档派生图。
         *
         * @return true 表示成功
         */
        public boolean generated() {
            return thumbKey != null && previewKey != null;
        }
    }
}
