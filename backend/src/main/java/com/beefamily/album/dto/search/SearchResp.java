package com.beefamily.album.dto.search;

import com.beefamily.album.dto.album.AlbumResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.dto.photo.PhotoResp;

/**
 * 全局搜索结果（三组）。
 *
 * <p>关键词原样返回在 items 中，**高亮由前端做**（必须先 HTML 转义再包 {@code <mark>}，
 * 见 §11.7 禁止项①），后端不参与任何 HTML 生成。</p>
 *
 * @param query       查询词
 * @param totalCount  三组命中总数
 * @param photos      照片分组
 * @param albums      相册分组
 * @param milestones  里程碑分组
 */
public record SearchResp(String query, long totalCount, SearchGroupResp<PhotoResp> photos,
                         SearchGroupResp<AlbumResp> albums, SearchGroupResp<MilestoneResp> milestones) {
}
