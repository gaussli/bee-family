package com.beefamily.album.dto.milestone;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 里程碑响应。
 *
 * <p>{@code photoThumbKey} 是「可直接用于 img 的 key」：前端不拼后缀、不查照片（§11.7）。</p>
 *
 * @param id            里程碑 id
 * @param babyId        宝宝 id，家庭级为 null
 * @param babyName      宝宝昵称，家庭级为 null
 * @param title         标题
 * @param description   描述
 * @param achievedAt    达成日期
 * @param ageText       达成时的年龄文案，家庭级为 null
 * @param photoId       配图照片 id
 * @param photoThumbKey 配图缩略图 key
 * @param createdBy     创建者 id
 * @param createdByName 创建者昵称
 * @param createdAt     创建时间
 */
public record MilestoneResp(Long id, Long babyId, String babyName, String title, String description,
                            LocalDate achievedAt, String ageText, Long photoId, String photoThumbKey,
                            Long createdBy, String createdByName, LocalDateTime createdAt) {
}
