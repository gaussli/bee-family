package com.beefamily.album.dto.growth;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成长记录请求（POST 为 UPSERT，PATCH 为全量覆盖）。
 *
 * <p>「三项至少一项非空」是跨字段约束，record 无法用单字段注解表达，
 * 由 {@code GrowthService} 校验并抛 40001（文案：请至少填写身高 / 体重 / 头围中的一项）。</p>
 *
 * @param recordedAt 测量日期（不得为未来）
 * @param heightCm   身高 30.0-150.0
 * @param weightKg   体重 1.00-60.00
 * @param headCm     头围 20.0-70.0
 * @param note       备注
 */
public record GrowthRecordReq(
        @NotNull(message = "不能为空")
        @PastOrPresent(message = "测量日期不能是未来日期")
        LocalDate recordedAt,

        @DecimalMin(value = "30.0", message = "身高需在 30.0-150.0 cm 之间")
        @DecimalMax(value = "150.0", message = "身高需在 30.0-150.0 cm 之间")
        BigDecimal heightCm,

        @DecimalMin(value = "1.00", message = "体重需在 1.00-60.00 kg 之间")
        @DecimalMax(value = "60.00", message = "体重需在 1.00-60.00 kg 之间")
        BigDecimal weightKg,

        @DecimalMin(value = "20.0", message = "头围需在 20.0-70.0 cm 之间")
        @DecimalMax(value = "70.0", message = "头围需在 20.0-70.0 cm 之间")
        BigDecimal headCm,

        @Size(max = 200, message = "备注不能超过 200 个字符")
        String note) {

    /**
     * 是否三项测量值全为空。
     *
     * @return true 表示无有效测量项
     */
    public boolean isEmptyMeasure() {
        return heightCm == null && weightKg == null && headCm == null;
    }
}
