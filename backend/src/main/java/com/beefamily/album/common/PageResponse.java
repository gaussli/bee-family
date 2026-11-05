package com.beefamily.album.common;

import java.util.List;

/**
 * 分页响应体。
 *
 * @param items      当前页数据，空集合输出 []（不输出 null）
 * @param page       当前页码（0 基）
 * @param size       每页条数
 * @param total      总条数
 * @param totalPages 总页数
 * @param hasMore    是否还有下一页
 * @param <T>        元素类型
 */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages, boolean hasMore) {

    /**
     * 由查询结果构建分页响应。
     *
     * @param items 当前页数据
     * @param query 归一化后的分页参数
     * @param total 总条数
     * @param <T>   元素类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> of(List<T> items, PageQuery query, long total) {
        int size = query.size() <= 0 ? PageQuery.DEFAULT_LIST_SIZE : query.size();
        long safeTotal = Math.max(total, 0L);
        int totalPages = (int) ((safeTotal + size - 1) / size);
        boolean hasMore = (long) (query.page() + 1) * size < safeTotal;
        return new PageResponse<>(items == null ? List.of() : items, query.page(), size, safeTotal, totalPages, hasMore);
    }
}
