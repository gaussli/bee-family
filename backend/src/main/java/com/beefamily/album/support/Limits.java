package com.beefamily.album.support;

import java.time.Duration;
import java.util.List;

/**
 * 业务阈值与固定文案常量。
 *
 * <p>集中放置所有「会在验收标准里被引用」的魔法值：家庭成员上限、宝宝上限、上传体积、缩略图档位、
 * 邀请码规格等。禁止在 service/controller 中散落裸数字（docs T09/T10/T11 的可维护性要求）。</p>
 */
public final class Limits {

    /** 单家庭成员上限（PRD P0-02）。 */
    public static final int FAMILY_MEMBER_LIMIT = 6;

    /** 单家庭宝宝档案上限（PRD P0-03）。 */
    public static final int BABY_LIMIT = 3;

    /** 单张照片体积上限：20MB。 */
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;

    /** 单批上传 / 批量操作条数上限。 */
    public static final int MAX_BATCH_SIZE = 100;

    /** 单批上传条数下限。 */
    public static final int MIN_BATCH_SIZE = 1;

    /** 图片像素上限（1 亿），用于防御解压炸弹。 */
    public static final long MAX_IMAGE_PIXELS = 100_000_000L;

    /** 缩略图最长边（网格 / 时间轴 / 封面）。 */
    public static final int THUMB_MAX_EDGE = 480;

    /** 预览图最长边（灯箱）。 */
    public static final int PREVIEW_MAX_EDGE = 1600;

    /** 缩略图 JPEG 质量。 */
    public static final float THUMB_QUALITY = 0.82f;

    /** 预览图 JPEG 质量。 */
    public static final float PREVIEW_QUALITY = 0.85f;

    /** 邀请码长度。 */
    public static final int INVITE_CODE_LENGTH = 8;

    /** 邀请码有效期。 */
    public static final Duration INVITE_CODE_TTL = Duration.ofHours(24);

    /** 标签名长度上限。 */
    public static final int TAG_NAME_MAX_LENGTH = 20;

    /** 标签列表返回条数上限。 */
    public static final int TAG_LIST_LIMIT = 20;

    /** 搜索关键词长度上限（防止恶意长串拖慢 LIKE 查询）。 */
    public static final int SEARCH_KEYWORD_MAX_LENGTH = 50;

    /** 搜索每组返回条数上限。 */
    public static final int SEARCH_GROUP_LIMIT = 20;

    /** HEIC 不支持时的用户指引（必须包含「iPhone」与「兼容性最佳」两个关键词，ADR-01）。 */
    public static final String HEIC_HINT =
            "不支持 HEIC 格式。请在 iPhone「设置 → 相机 → 格式」中选择「兼容性最佳」后重新拍摄，"
                    + "或在相册中选择图片「导出为 JPEG」后再上传。";

    /** 不支持的格式通用提示后缀。 */
    public static final String UNSUPPORTED_HINT = "仅支持 jpg / jpeg / png / webp 格式的图片。";

    /** 允许的图片扩展名（小写）。 */
    public static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    /** 明确拒绝的扩展名（可执行 / 可内嵌脚本 / 不支持的图片容器）。 */
    public static final List<String> BLOCKED_EXTENSIONS = List.of(
            "svg", "svgz", "heic", "heif", "exe", "sh", "bat", "cmd", "js", "mjs", "html", "htm",
            "php", "jsp", "jar", "zip", "rar", "7z", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "gif", "bmp", "tif", "tiff", "ico", "psd", "mov", "mp4");

    private Limits() {
        // 工具类禁止实例化
    }
}
