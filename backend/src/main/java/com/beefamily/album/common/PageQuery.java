package com.beefamily.album.common;

/**
 * 分页请求参数（page 为 0 基）。
 *
 * @param page 页码，0 基
 * @param size 每页条数
 */
public record PageQuery(int page, int size) {

    /** 时间轴默认每页条数。 */
    public static final int DEFAULT_TIMELINE_SIZE = 24;
    /** 照片网格默认每页条数。 */
    public static final int DEFAULT_PHOTO_SIZE = 60;
    /** 列表页（成长记录 / 里程碑 / 搜索）默认每页条数。 */
    public static final int DEFAULT_LIST_SIZE = 20;
    /** 每页条数上限。 */
    public static final int MAX_SIZE = 100;

    /**
     * 归一化：页码不小于 0，条数落在 (0, {@value #MAX_SIZE}]。
     *
     * @param defaultSize 未传 size 时使用的默认值
     * @return 归一化后的分页参数
     */
    public PageQuery normalize(int defaultSize) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? defaultSize : Math.min(size, MAX_SIZE);
        return new PageQuery(safePage, safeSize);
    }

    /**
     * 构建并归一化分页参数。
     *
     * @param page        页码
     * @param size        每页条数
     * @param defaultSize 默认每页条数
     * @return 归一化后的分页参数
     */
    public static PageQuery of(int page, int size, int defaultSize) {
        return new PageQuery(page, size).normalize(defaultSize);
    }

    /**
     * SQL OFFSET。
     *
     * @return 偏移量
     */
    public int offset() {
        return page * size;
    }
}
