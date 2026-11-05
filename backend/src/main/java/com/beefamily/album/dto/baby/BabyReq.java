package com.beefamily.album.dto.baby;

import com.beefamily.album.domain.enums.Gender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建宝宝请求。
 *
 * <p>阈值与前端 {@code utils/validate.ts} 必须保持同一套（§11.7「表单校验双写」）。
 * 生日用 {@code @PastOrPresent}，错误文案即完整句子，便于聚合后仍可读。</p>
 *
 * @param name          昵称
 * @param gender        性别
 * @param birthday      生日（不得晚于今天）
 * @param avatarUrl     头像
 * @param birthHeightCm 出生身高 cm（20.0-80.0）
 * @param birthWeightKg 出生体重 kg（0.50-10.00）
 */
public record BabyReq(
        @NotBlank(message = "不能为空")
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String name,

        Gender gender,

        @NotNull(message = "不能为空")
        @PastOrPresent(message = "生日不能是未来日期")
        LocalDate birthday,

        @Size(max = 255, message = "长度不能超过 255 个字符")
        String avatarUrl,

        @DecimalMin(value = "20.0", message = "出生身高需在 20.0-80.0 cm 之间")
        @DecimalMax(value = "80.0", message = "出生身高需在 20.0-80.0 cm 之间")
        BigDecimal birthHeightCm,

        @DecimalMin(value = "0.50", message = "出生体重需在 0.50-10.00 kg 之间")
        @DecimalMax(value = "10.00", message = "出生体重需在 0.50-10.00 kg 之间")
        BigDecimal birthWeightKg) {
}
