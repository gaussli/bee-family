package com.beefamily.album.dto.photo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新照片请求（PATCH）。
 *
 * <p><b>语义例外</b>：其余字段为「全量提交」（null 即置空），但 {@code tags} 是**完整集合语义** ——
 * 传 {@code []} 清空、传 {@code null} 表示不修改标签（§11.4 明确例外，实现见 PhotoService#applyTags）。</p>
 *
 * @param title        标题 ≤80
 * @param description  描述 ≤500
 * @param takenAt      拍摄时间（null 表示不使用自定义值，保持原值）
 * @param locationName 地点 ≤100
 * @param albumId      目标相册 id；null = 移出相册（未分类），见 U-3
 * @param tags         完整标签名集合
 */
public record PhotoUpdateReq(
        @Size(max = 80, message = "标题不能超过 80 个字符")
        String title,

        @Size(max = 500, message = "描述不能超过 500 个字符")
        String description,

        LocalDateTime takenAt,

        @Size(max = 100, message = "地点不能超过 100 个字符")
        String locationName,

        Long albumId,

        List<@NotBlank(message = "标签不能为空") @Size(max = 20, message = "单个标签不能超过 20 个字符") String> tags) {
}
