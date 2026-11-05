package com.beefamily.album.dto.growth;

import java.math.BigDecimal;

/**
 * 环比增量 = 最新记录 − 上一条记录（PRD 成长页「+1.2 较上月」）。
 *
 * <p>任一侧为空则对应字段为 null（§5.6）。</p>
 *
 * @param heightCm 身高增量
 * @param weightKg 体重增量
 * @param headCm   头围增量
 */
public record GrowthDeltaResp(BigDecimal heightCm, BigDecimal weightKg, BigDecimal headCm) {
}
