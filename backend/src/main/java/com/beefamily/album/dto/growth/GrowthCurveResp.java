package com.beefamily.album.dto.growth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 成长曲线响应。
 *
 * @param babyId   宝宝 id
 * @param babyName 宝宝昵称
 * @param birthday 生日
 * @param range    区间：3m / 6m / 12m / all
 * @param from     区间起点（含），all 时为最早记录日期
 * @param to       区间终点（含），all 时为最晚记录日期
 * @param points   数据点，**按日期升序**（ECharts type:'time' 要求）
 * @param latest   最新一条测量点
 * @param delta    环比增量
 */
public record GrowthCurveResp(Long babyId, String babyName, LocalDate birthday, String range, LocalDate from,
                              LocalDate to, List<CurvePointResp> points, LatestPoint latest,
                              GrowthDeltaResp delta) {

    /**
     * 最新测量点（不含 ageDays，与前端 {@code latest} 结构一致）。
     *
     * @param recordedAt 测量日期
     * @param heightCm   身高
     * @param weightKg   体重
     * @param headCm     头围
     */
    public record LatestPoint(LocalDate recordedAt, BigDecimal heightCm, BigDecimal weightKg, BigDecimal headCm) {
    }
}
