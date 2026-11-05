package com.beefamily.album.dto.growth;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 曲线数据点。
 *
 * @param recordedAt 测量日期
 * @param ageDays    测量当天的日龄（ECharts X 轴可用 time 或按此均匀分布）
 * @param heightCm   身高
 * @param weightKg   体重
 * @param headCm     头围
 */
public record CurvePointResp(LocalDate recordedAt, int ageDays, BigDecimal heightCm, BigDecimal weightKg,
                             BigDecimal headCm) {
}
