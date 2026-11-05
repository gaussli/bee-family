package com.beefamily.album.dto.timeline;

import java.util.List;

/**
 * 时间轴分页响应。
 *
 * <p>{@code hasMore} 由 {@code size+1} 探测决定（不信 {@code total}），避免 count 与数据不一致时
 * 前端无限翻页（§5.7 关键决策 4）。</p>
 *
 * @param groups     月份分组
 * @param page       当前页（0 基）
 * @param size       每页条数
 * @param total      三源条目总数
 * @param totalPages 总页数
 * @param hasMore    是否还有下一页
 */
public record TimelinePageResp(List<TimelineGroupResp> groups, int page, int size, long total, int totalPages,
                               boolean hasMore) {
}
