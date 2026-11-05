package com.beefamily.album.dto.milestone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 里程碑请求（创建 / 全量更新）。
 *
 * @param babyId      宝宝 id，null 表示家庭级里程碑
 * @param title       标题 1-60
 * @param description 描述 ≤500
 * @param achievedAt  达成日期（不得为未来）
 * @param photoId     配图照片 id，可为 null（必须属同一家庭）
 */
public record MilestoneReq(
        Long babyId,

        @NotBlank(message = "不能为空")
        @Size(max = 60, message = "长度不能超过 60 个字符")
        String title,

        @Size(max = 500, message = "长度不能超过 500 个字符")
        String description,

        @NotNull(message = "不能为空")
        @PastOrPresent(message = "达成日期不能是未来日期")
        LocalDate achievedAt,

        Long photoId) {
}
