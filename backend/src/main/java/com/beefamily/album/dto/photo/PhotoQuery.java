package com.beefamily.album.dto.photo;

import com.beefamily.album.common.PageQuery;

/**
 * 照片列表查询条件。
 *
 * @param albumId      相册 id（与 unclassified 互斥）
 * @param unclassified 是否只看未分类
 * @param page         页码（0 基）
 * @param size         每页条数
 * @param sort         排序白名单：takenAt_desc / takenAt_asc / createdAt_desc
 */
public record PhotoQuery(Long albumId, boolean unclassified, int page, int size, String sort) {

    /** 默认排序：拍摄时间倒序。 */
    public static final String SORT_TAKEN_DESC = "takenAt_desc";
    /** 拍摄时间正序。 */
    public static final String SORT_TAKEN_ASC = "takenAt_asc";
    /** 上传时间倒序（「最近添加」）。 */
    public static final String SORT_CREATED_DESC = "createdAt_desc";

    /**
     * 是否合法的排序值（白名单三值，其余 40002）。
     *
     * @param value 排序参数
     * @return true 表示合法
     */
    public static boolean isSupportedSort(String value) {
        return SORT_TAKEN_DESC.equals(value) || SORT_TAKEN_ASC.equals(value) || SORT_CREATED_DESC.equals(value);
    }

    /**
     * 转分页参数。
     *
     * @return 归一化后的分页参数
     */
    public PageQuery pageQuery() {
        return PageQuery.of(page, size, PageQuery.DEFAULT_PHOTO_SIZE);
    }
}
