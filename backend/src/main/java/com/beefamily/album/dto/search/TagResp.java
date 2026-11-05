package com.beefamily.album.dto.search;

import java.time.LocalDateTime;

/**
 * 标签响应。
 *
 * @param id         标签 id
 * @param name       标签名
 * @param photoCount 使用该标签的照片数
 * @param lastUsedAt 最近一次被使用的时间（无关联时为 null）
 */
public record TagResp(Long id, String name, long photoCount, LocalDateTime lastUsedAt) {
}
