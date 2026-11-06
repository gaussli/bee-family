package com.beefamily.album.support;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 图片校验器：白名单魔数嗅探 + 扩展名黑名单 + HEIC / SVG 明确拒绝。
 *
 * <p><b>为什么以魔数为唯一可信依据</b>：{@code Content-Type} 与文件名后缀都可以被客户端伪造，
 * 只有文件头字节无法伪造成另一种合法图片（伪造即无法解码）。因此判定顺序是
 * 「先嗅探 → 再参考扩展名做附加拦截」，扩展名从不能「证明」格式。</p>
 *
 * <p><b>为什么明确拒绝 SVG</b>：SVG 可内嵌 {@code <script>}，是典型的存储型 XSS 入口；
 * 本项目图片通过 {@code <img>} 展示，虽然 {@code <img>} 不执行脚本，但下载原图后直接打开仍会触发。</p>
 *
 * <p><b>T01 阶段仍明确拒绝 HEIC</b>：v1.1 的纯 Java 解码与 EXIF 解析由 T03 接入
 * （docs/07 §11.1），基础设施阶段沿用原有的明确拒绝，避免「上传成功但无法展示」。
 * 拒绝文案统一走 {@link Limits#UNSUPPORTED_HINT}，不含旧 ADR-01 的可用性措辞。</p>
 */
public final class ImageValidator {

    /** 嗅探需要的最小字节数（覆盖 RIFF/WEBP 的 offset 8 + 4 字节 brand）。 */
    private static final int SNIFF_LENGTH = 32;

    /** ISO-BMFF 的 ftyp brand 中属于 HEIC/HEIF 家族的取值。 */
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif");

    private ImageValidator() {
    }

    /**
     * 仅做魔数嗅探（不含业务异常），供单元测试与特殊场景使用。
     *
     * @param header 文件头字节（至少 12 字节，建议 32 字节）
     * @return 识别出的类型
     */
    public static ImageType sniff(byte[] header) {
        if (header == null || header.length < 12) {
            return ImageType.UNKNOWN;
        }
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (matchesAscii(header, 0, "RIFF") && matchesAscii(header, 8, "WEBP")) {
            return ImageType.WEBP;
        }
        if (matchesAscii(header, 4, "ftyp")) {
            String brand = new String(header, 8, 4, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            if (HEIC_BRANDS.contains(brand)) {
                return ImageType.HEIC;
            }
        }
        if (looksLikeSvg(header)) {
            return ImageType.SVG;
        }
        return ImageType.UNKNOWN;
    }

    /**
     * 校验上传文件，通过则返回真实类型，否则抛 {@link ErrorCode#UNSUPPORTED_MEDIA_TYPE}。
     *
     * @param bytes    文件内容
     * @param fileName 客户端文件名（仅用于扩展名黑名单与失败定位，不能作为格式依据）
     * @return 真实图片类型（只会是 JPEG / PNG / WEBP）
     */
    public static ImageType validate(byte[] bytes, String fileName) {
        byte[] header = bytes == null ? new byte[0] : bytes;
        ImageType sniffed = sniff(header.length > SNIFF_LENGTH ? slice(header, SNIFF_LENGTH) : header);

        if (sniffed == ImageType.HEIC) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, Limits.UNSUPPORTED_HINT);
        }
        if (sniffed == ImageType.SVG || isBlockedExtension(fileName)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "不支持该文件格式。" + Limits.UNSUPPORTED_HINT);
        }
        if (!sniffed.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "无法识别的图片格式。" + Limits.UNSUPPORTED_HINT);
        }
        return sniffed;
    }

    /**
     * 扩展名是否在明确拒绝清单内。
     *
     * @param fileName 文件名
     * @return true 表示拒绝
     */
    public static boolean isBlockedExtension(String fileName) {
        String extension = extensionOf(fileName);
        return extension != null && Limits.BLOCKED_EXTENSIONS.contains(extension);
    }

    /**
     * 提取小写扩展名。
     *
     * @param fileName 文件名
     * @return 扩展名，无扩展名返回 null
     */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 截取前 n 字节。
     *
     * @param source 源数组
     * @param length 长度
     * @return 新数组
     */
    private static byte[] slice(byte[] source, int length) {
        byte[] target = new byte[length];
        System.arraycopy(source, 0, target, 0, length);
        return target;
    }

    /**
     * 按字节比较（int 形式的掩码比较，避免 byte 符号扩展问题）。
     *
     * @param data   数据
     * @param offset 起始偏移
     * @param expected 期望字节（0-255）
     * @return 是否匹配
     */
    private static boolean matches(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按 ASCII 字符串比较。
     *
     * @param data   数据
     * @param offset 起始偏移
     * @param ascii  期望字符串
     * @return 是否匹配
     */
    private static boolean matchesAscii(byte[] data, int offset, String ascii) {
        if (data.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (data[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 内容是否像 SVG（跳过 BOM 与首部空白后以 {@code <svg} 或 {@code <?xml} 开头）。
     *
     * @param header 文件头
     * @return true 表示疑似 SVG
     */
    private static boolean looksLikeSvg(byte[] header) {
        int start = 0;
        // UTF-8 BOM
        if (header.length > 3 && (header[0] & 0xFF) == 0xEF && (header[1] & 0xFF) == 0xBB
                && (header[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        while (start < header.length && Character.isWhitespace((char) (header[start] & 0xFF))) {
            start++;
        }
        if (header.length - start < 4) {
            return false;
        }
        String head = new String(header, start, Math.min(16, header.length - start), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        return head.startsWith("<svg") || head.startsWith("<?xml");
    }
}
