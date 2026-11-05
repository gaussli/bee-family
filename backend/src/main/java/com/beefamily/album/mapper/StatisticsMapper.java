package com.beefamily.album.mapper;

import com.beefamily.album.dto.stats.OverviewResp;

/**
 * 统计实体 → DTO。
 *
 * <p>统计全部为「实时聚合」结果（PRD P0-12）：没有快照表、没有缓存，因此这里只做字段装配。</p>
 */
public final class StatisticsMapper {

    private StatisticsMapper() {
    }

    /**
     * 组装概览响应。
     *
     * @param photoCount          照片总数
     * @param albumCount          相册总数
     * @param milestoneCount      里程碑总数
     * @param memberCount         成员数
     * @param babyCount           宝宝数
     * @param earliestBabyAgeDays 最早宝宝日龄（无宝宝时为 null）
     * @param monthPhotoCount     本月新增照片数
     * @param storageBytes        存储占用
     * @return 概览响应
     */
    public static OverviewResp toOverview(long photoCount, long albumCount, long milestoneCount, long memberCount,
                                          long babyCount, Integer earliestBabyAgeDays, long monthPhotoCount,
                                          long storageBytes) {
        return new OverviewResp(photoCount, albumCount, milestoneCount, memberCount, babyCount,
                earliestBabyAgeDays, monthPhotoCount, storageBytes, null);
    }
}
