package com.beefamily.album.dto.album;

import java.util.List;

/**
 * 相册列表响应 = 真实相册 + 未分类虚拟相册。
 *
 * @param items        真实相册（按 updatedAt 倒序）
 * @param unclassified 未分类汇总
 */
public record AlbumListResp(List<AlbumResp> items, UnclassifiedResp unclassified) {
}
