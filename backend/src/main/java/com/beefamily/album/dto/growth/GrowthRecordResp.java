package com.beefamily.album.dto.growth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成长记录响应。
 *
 * <p>必须含 {@code babyName}：时间轴与前端列表直接展示，避免为每条记录再查一次宝宝（T07 关键约束④）。
 * {@code BigDecimal} 直接透出，保留 DB scale（9.80 不变成 9.8），禁止 {@code stripTrailingZeros}。</p>
 *
 * @param id              记录 id
 * @param babyId          宝宝 id
 * @param babyName        宝宝昵称
 * @param recordedAt      测量日期
 * @param heightCm        身高
 * @param weightKg        体重
 * @param headCm          头围
 * @param note            备注
 * @param createdBy       记录人 user id
 * @param createdByName   记录人昵称
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record GrowthRecordResp(Long id, Long babyId, String babyName, LocalDate recordedAt, BigDecimal heightCm,
                               BigDecimal weightKg, BigDecimal headCm, String note, Long createdBy,
                               String createdByName, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
