package com.beefamily.album.dto.photo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新照片请求（PATCH）。
 *
 * <p><b>字段语义（§11.4「全量提交」+ 两处明确例外）</b>：</p>
 * <ul>
 *   <li>{@code title} / {@code description} / {@code locationName}：全量提交，{@code null} 即置空；</li>
 *   <li>{@code takenAt}：{@code null} 表示不修改（列是 {@code NOT NULL}，置空会写非法值）；</li>
 *   <li>{@code tags}：**完整集合语义** —— 传 {@code []} 清空、传 {@code null} 表示不修改标签；</li>
 *   <li>{@code albumId}：三态（{@link PatchField}）—— **缺席 = 不修改**、{@code null} = 移到未分类、
 *       数字 = 移到该相册。前端编辑标题 / 标签时不会带这个字段，绝不能把「缺席」当成「移出相册」。</li>
 * </ul>
 *
 * @param title        标题 ≤80
 * @param description  描述 ≤500
 * @param takenAt      拍摄时间（null 表示不使用自定义值，保持原值）
 * @param locationName 地点 ≤100
 * @param albumId      目标相册：缺席 = 不修改，null = 移出相册（未分类），数字 = 移到该相册
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

        PatchField<Long> albumId,

        List<@NotBlank(message = "标签不能为空") @Size(max = 20, message = "单个标签不能超过 20 个字符") String> tags) {
}
