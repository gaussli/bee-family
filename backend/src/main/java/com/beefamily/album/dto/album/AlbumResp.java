package com.beefamily.album.dto.album;

import java.time.LocalDateTime;

/**
 * 相册响应。
 *
 * <p>{@code coverThumbKey} 由服务端解析：优先用户设置的 cover，否则取相册内 {@code taken_at} 最新的照片
 * （PRD P0-06③）。前端直接用，不做二次查询与字符串拼接。</p>
 *
 * @param id            相册 id
 * @param name          相册名
 * @param description   描述
 * @param coverPhotoId  封面照片 id（可能为 null）
 * @param coverThumbKey 封面缩略图 key（可能为 null）
 * @param photoCount    照片数（冗余计数）
 * @param createdBy     创建者 id
 * @param createdByName 创建者昵称
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param latestTakenAt 相册内最新拍摄时间（用于「最近更新」展示与详情页日期范围）
 */
public record AlbumResp(Long id, String name, String description, Long coverPhotoId, String coverThumbKey,
                        int photoCount, Long createdBy, String createdByName, LocalDateTime createdAt,
                        LocalDateTime updatedAt, LocalDateTime latestTakenAt) {
}
