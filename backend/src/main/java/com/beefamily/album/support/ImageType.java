package com.beefamily.album.support;

/**
 * 图片真实类型（由文件头魔数判定，不信任客户端后缀与 Content-Type）。
 *
 * <p>{@code extension} 用于服务端生成文件名；{@code mimeType} 写入 {@code photo.mime_type}。
 * 只有 {@link #JPEG} / {@link #PNG} / {@link #WEBP} 是白名单，其余一律拒绝（§7.6）。</p>
 */
public enum ImageType {

    /** JPEG。 */
    JPEG("jpg", "image/jpeg"),

    /** PNG（保留透明通道，缩略图仍输出 png）。 */
    PNG("png", "image/png"),

    /** WEBP。 */
    WEBP("webp", "image/webp"),

    /** HEIC / HEIF：明确不支持（ADR-01）。 */
    HEIC("heic", "image/heic"),

    /** SVG：含脚本风险，明确拒绝。 */
    SVG("svg", "image/svg+xml"),

    /** 未知 / 其他格式。 */
    UNKNOWN("bin", "application/octet-stream");

    private final String extension;
    private final String mimeType;

    ImageType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    /**
     * 文件扩展名（不带点）。
     *
     * @return 扩展名
     */
    public String getExtension() {
        return extension;
    }

    /**
     * MIME 类型。
     *
     * @return MIME
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * 是否白名单格式（可入库）。
     *
     * @return true 表示支持
     */
    public boolean isSupported() {
        return this == JPEG || this == PNG || this == WEBP;
    }
}
