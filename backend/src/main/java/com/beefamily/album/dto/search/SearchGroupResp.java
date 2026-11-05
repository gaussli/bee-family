package com.beefamily.album.dto.search;

import java.util.List;

/**
 * 搜索分组结果。
 *
 * @param total   命中总数（可能大于 items.size()）
 * @param items   当前返回条目（≤20）
 * @param hasMore 是否还有更多（前端展示「查看全部」）
 * @param <T>     条目类型
 */
public record SearchGroupResp<T>(long total, List<T> items, boolean hasMore) {

    /**
     * 由总数与当前条目构建。
     *
     * @param total 命中总数
     * @param items 当前条目
     * @param <T>   条目类型
     * @return 分组结果
     */
    public static <T> SearchGroupResp<T> of(long total, List<T> items) {
        List<T> safeItems = items == null ? List.of() : items;
        return new SearchGroupResp<>(total, safeItems, total > safeItems.size());
    }
}
