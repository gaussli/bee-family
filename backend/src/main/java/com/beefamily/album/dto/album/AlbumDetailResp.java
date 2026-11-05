package com.beefamily.album.dto.album;

import java.time.LocalDateTime;

/**
 * 相册详情响应 = {@link AlbumResp} 全部字段 + 拍摄时间范围 + 当前用户可编辑标记。
 *
 * <p>{@code ownerEditable} 只用于前端按钮显隐，不是安全边界（服务端每次都重新判定）。</p>
 *
 * @param id            相册 id
 * @param name          相册名
 * @param description   描述
 * @param coverPhotoId  封面照片 id
 * @param coverThumbKey 封面缩略图 key
 * @param photoCount    照片数
 * @param createdBy     创建者 id
 * @param createdByName 创建者昵称
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param latestTakenAt 最新拍摄时间
 * @param takenFrom     最早拍摄日期
 * @param takenTo       最晚拍摄日期
 * @param ownerEditable 当前用户是否可编辑（创建者或 owner）
 */
public record AlbumDetailResp(Long id, String name, String description, Long coverPhotoId, String coverThumbKey,
                              int photoCount, Long createdBy, String createdByName, LocalDateTime createdAt,
                              LocalDateTime updatedAt, LocalDateTime latestTakenAt,
                              LocalDateTime takenFrom, LocalDateTime takenTo, boolean ownerEditable) {
}
