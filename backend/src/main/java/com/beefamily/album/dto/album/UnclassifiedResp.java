package com.beefamily.album.dto.album;

/**
 * 未分类虚拟相册（PRD Q6：不作为真实表行，随 {@code GET /api/albums} 一并返回）。
 *
 * @param count         未分类照片数
 * @param coverPhotoId  未分类中最新照片 id
 * @param coverThumbKey 对应缩略图 key
 */
public record UnclassifiedResp(long count, Long coverPhotoId, String coverThumbKey) {
}
