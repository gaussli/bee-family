package com.beefamily.album.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ImageValidator} 单元测试。
 *
 * <p>核心命题（docs §7.6、ADR-01）：**格式判定只信文件头魔数，不信任客户端给的后缀 /
 * Content-Type**。因此「伪装成 .jpg 的文本文件」必须拒绝，这是本项目最重要的上传安全断言。</p>
 */
class ImageValidatorTest {

    /** JPEG 文件头（FF D8 FF E0 + 长度 + JFIF 标识）。 */
    private static final byte[] JPEG_HEADER = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01
    };

    /** PNG 文件头。 */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    /** WEBP 文件头：RIFF + 长度 + WEBP。 */
    private static final byte[] WEBP_HEADER = {
            'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'
    };

    /** HEIC 文件头：长度 + ftyp + heic。 */
    private static final byte[] HEIC_HEADER = {
            0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'
    };

    /**
     * 在给定头部之后补足字节，模拟真实文件长度（嗅探只看前 32 字节）。
     *
     * @param header 文件头
     * @param total  目标总长度
     * @return 新数组
     */
    private static byte[] padded(byte[] header, int total) {
        byte[] result = new byte[Math.max(total, header.length)];
        System.arraycopy(header, 0, result, 0, header.length);
        return result;
    }

    /**
     * 断言抛出 41501 且文案包含指定片段。
     *
     * @param bytes    文件内容
     * @param fileName 文件名
     * @param expected 文案片段
     */
    private void assertRejected(byte[] bytes, String fileName, String expected) {
        assertThatThrownBy(() -> ImageValidator.validate(bytes, fileName))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(expected)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Nested
    @DisplayName("魔数嗅探")
    class Sniff {

        @Test
        @DisplayName("JPEG / PNG / WEBP 魔数被正确识别")
        void recognisesSupportedMagicNumbers() {
            assertThat(ImageValidator.sniff(padded(JPEG_HEADER, 32))).isEqualTo(ImageType.JPEG);
            assertThat(ImageValidator.sniff(padded(PNG_HEADER, 32))).isEqualTo(ImageType.PNG);
            assertThat(ImageValidator.sniff(padded(WEBP_HEADER, 32))).isEqualTo(ImageType.WEBP);
        }

        @Test
        @DisplayName("HEIC 的 ftyp brand 被识别为 HEIC（大小写不敏感）")
        void recognisesHeicBrand() {
            assertThat(ImageValidator.sniff(padded(HEIC_HEADER, 32))).isEqualTo(ImageType.HEIC);

            byte[] upper = HEIC_HEADER.clone();
            upper[8] = 'H';
            upper[9] = 'E';
            upper[10] = 'I';
            upper[11] = 'C';
            assertThat(ImageValidator.sniff(padded(upper, 32))).isEqualTo(ImageType.HEIC);
        }

        @Test
        @DisplayName("不足 12 字节一律 UNKNOWN（避免短文件触发误判）")
        void tooShortIsUnknown() {
            assertThat(ImageValidator.sniff(new byte[0])).isEqualTo(ImageType.UNKNOWN);
            assertThat(ImageValidator.sniff(null)).isEqualTo(ImageType.UNKNOWN);
            assertThat(ImageValidator.sniff(new byte[11])).isEqualTo(ImageType.UNKNOWN);
        }

        @Test
        @DisplayName("只有 RIFF 头、offset 8 不是 WEBP → UNKNOWN（不能只凭前 4 字节放行）")
        void riffWithoutWebpIsUnknown() {
            byte[] riffOnly = {
                    'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'A', 'V', 'I', ' ', 0, 0, 0, 0
            };
            assertThat(ImageValidator.sniff(riffOnly)).isEqualTo(ImageType.UNKNOWN);
        }

        @ParameterizedTest(name = "SVG 变体 {0} 被判为 SVG")
        @ValueSource(strings = {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "   \n<svg></svg>",
                "<?xml version=\"1.0\"?><svg></svg>",
                "\uFEFF<svg></svg>"
        })
        @DisplayName("XML / SVG 文本被判为 SVG（含 BOM 与前导空白）")
        void detectsSvg(String content) {
            byte[] bytes = padded(content.getBytes(StandardCharsets.UTF_8), 64);
            assertThat(ImageValidator.sniff(bytes)).isEqualTo(ImageType.SVG);
        }
    }

    @Nested
    @DisplayName("validate：白名单放行")
    class Accepts {

        @Test
        @DisplayName("真实 JPEG 魔数 + .jpg 后缀 → 通过并返回 JPEG")
        void acceptsRealJpeg() {
            assertThat(ImageValidator.validate(padded(JPEG_HEADER, 128), "IMG_0001.jpg")).isEqualTo(ImageType.JPEG);
        }

        @Test
        @DisplayName("PNG / WEBP 通过")
        void acceptsPngAndWebp() {
            assertThat(ImageValidator.validate(padded(PNG_HEADER, 128), "a.png")).isEqualTo(ImageType.PNG);
            assertThat(ImageValidator.validate(padded(WEBP_HEADER, 128), "b.webp")).isEqualTo(ImageType.WEBP);
        }

        @Test
        @DisplayName("后缀为空时仍以魔数为准（魔数合法即通过）")
        void acceptsWhenFileNameMissing() {
            assertThat(ImageValidator.validate(padded(JPEG_HEADER, 64), null)).isEqualTo(ImageType.JPEG);
        }
    }

    @Nested
    @DisplayName("validate：拒绝")
    class Rejects {

        @Test
        @DisplayName("伪装成 .jpg 的纯文本文件必须拒绝（后缀不能作为格式依据）")
        void rejectsTextFileRenamedToJpg() {
            byte[] text = "这不是图片，只是被改名为 .jpg 的文本文件".getBytes(StandardCharsets.UTF_8);
            assertRejected(text, "holiday.jpg", "无法识别的图片格式");
        }

        @Test
        @DisplayName("空文件 / null 内容必须拒绝")
        void rejectsEmptyContent() {
            assertRejected(new byte[0], "empty.jpg", "无法识别的图片格式");
            assertRejected(null, "null.jpg", "无法识别的图片格式");
        }

        @Test
        @DisplayName(".svg 内容被拒绝，且提示只支持 jpg/jpeg/png/webp")
        void rejectsSvgContent() {
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
            assertRejected(svg, "logo.png", "仅支持 jpg / jpeg / png / webp 格式的图片");
        }

        @Test
        @DisplayName(".svg 后缀即便内容是合法 JPEG 也拒绝（脚本注入面直接拦在扩展名）")
        void rejectsSvgExtensionEvenWithJpegContent() {
            assertRejected(padded(JPEG_HEADER, 64), "evil.svg", "不支持该文件格式");
        }

        @Test
        @DisplayName(".exe 后缀被拒绝")
        void rejectsExe() {
            byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};
            assertRejected(padded(exe, 64), "payload.exe", "不支持该文件格式");
        }

        @Test
        @DisplayName(".heic 后缀被拒绝，提示必须含「iPhone」与「兼容性最佳」")
        void rejectsHeicExtensionWithActionableHint() {
            assertRejected(padded(HEIC_HEADER, 64), "IMG_0001.heic", "iPhone");
            assertThatThrownBy(() -> ImageValidator.validate(padded(HEIC_HEADER, 64), "IMG_0001.heic"))
                    .hasMessageContaining("兼容性最佳");
        }

        @Test
        @DisplayName("HEIC 魔数但后缀是 .jpg：仍按魔数判为 HEIC 并给出 iPhone 指引")
        void rejectsHeicMagicDisguisedAsJpg() {
            assertRejected(padded(HEIC_HEADER, 64), "disguised.jpg", "iPhone");
        }

        @ParameterizedTest(name = "{0} 属于拒绝清单")
        @ValueSource(strings = {"a.svg", "a.svgz", "a.heic", "a.HEIF", "a.exe", "a.sh", "a.bat", "a.html",
                "a.js", "a.php", "a.zip", "a.pdf", "a.gif", "a.bmp", "a.mp4", "a.tiff"})
        @DisplayName("拒绝清单内的扩展名大小写不敏感")
        void blockedExtensions(String fileName) {
            assertRejected(padded(JPEG_HEADER, 64), fileName, "不支持该文件格式");
        }
    }

    @Nested
    @DisplayName("扩展名解析")
    class Extension {

        @Test
        @DisplayName("无扩展名 / 以点结尾 / null 均返回 null")
        void noExtension() {
            assertThat(ImageValidator.extensionOf("photo")).isNull();
            assertThat(ImageValidator.extensionOf("photo.")).isNull();
            assertThat(ImageValidator.extensionOf(null)).isNull();
        }

        @Test
        @DisplayName("多点文件名取最后一段并小写化")
        void lastSegmentLowercased() {
            assertThat(ImageValidator.extensionOf("holiday.2026.JPG")).isEqualTo("jpg");
            assertThat(ImageValidator.extensionOf("A.SvG")).isEqualTo("svg");
        }

        @Test
        @DisplayName("isBlockedExtension 对白名单后缀返回 false")
        void allowedExtensionsAreNotBlocked() {
            assertThat(ImageValidator.isBlockedExtension("a.jpg")).isFalse();
            assertThat(ImageValidator.isBlockedExtension("a.jpeg")).isFalse();
            assertThat(ImageValidator.isBlockedExtension("a.png")).isFalse();
            assertThat(ImageValidator.isBlockedExtension("a.webp")).isFalse();
        }
    }
}
