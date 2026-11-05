package com.beefamily.album.dto.stats;

/**
 * 概览统计（PRD P0-12：实时聚合，禁止缓存）。
 *
 * <p>{@code storageLimitBytes} 本期恒为 null：单机自托管没有配额概念，字段保留以便前端进度条
 * 在 P1 接入配额后不用改契约。</p>
 *
 * @param photoCount          照片总数
 * @param albumCount          相册总数
 * @param milestoneCount      里程碑总数
 * @param memberCount         成员数
 * @param babyCount           宝宝数
 * @param earliestBabyAgeDays 最早出生宝宝的日龄（无宝宝时 null）
 * @param monthPhotoCount     本月新增照片数
 * @param storageBytes        存储占用字节数
 * @param storageLimitBytes   存储上限（本期 null）
 */
public record OverviewResp(long photoCount, long albumCount, long milestoneCount, long memberCount, long babyCount,
                           Integer earliestBabyAgeDays, long monthPhotoCount, long storageBytes,
                           Long storageLimitBytes) {
}
