package com.beefamily.album.dto.baby;

import com.beefamily.album.domain.enums.Gender;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 宝宝档案响应。
 *
 * @param id              宝宝 id
 * @param name            昵称
 * @param gender          性别
 * @param birthday        生日
 * @param avatarUrl       头像
 * @param birthHeightCm   出生身高
 * @param birthWeightKg   出生体重
 * @param ageDays         已出生天数（服务端按 Asia/Shanghai 计算）
 * @param ageText         年龄文案，如 {@code 1岁2个月4天}
 * @param milestoneCount  里程碑数
 * @param latestGrowth    最新一条成长记录，无记录时为 null
 */
public record BabyResp(Long id, String name, Gender gender, LocalDate birthday, String avatarUrl,
                       BigDecimal birthHeightCm, BigDecimal birthWeightKg, int ageDays, String ageText,
                       long milestoneCount, GrowthRecordResp latestGrowth) {
}
