package com.beefamily.album.dto.baby;

import com.beefamily.album.domain.enums.Gender;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 宝宝详情响应 = {@link BabyResp} 全部字段 + 里程碑 / 成长记录补充信息。
 *
 * <p>用独立 record（而非继承）是因为 record 不能继承 record；字段与前端 {@code BabyDetail}
 * interface 一一对应，顺序也保持一致，便于逐字段核对。</p>
 *
 * @param id                宝宝 id
 * @param name              昵称
 * @param gender            性别
 * @param birthday          生日
 * @param avatarUrl         头像
 * @param birthHeightCm     出生身高
 * @param birthWeightKg     出生体重
 * @param ageDays           已出生天数
 * @param ageText           年龄文案
 * @param milestoneCount    里程碑数
 * @param latestGrowth      最新成长记录
 * @param latestMilestone   最新里程碑
 * @param growthRecordCount 成长记录条数
 */
public record BabyDetailResp(Long id, String name, Gender gender, LocalDate birthday, String avatarUrl,
                             BigDecimal birthHeightCm, BigDecimal birthWeightKg, int ageDays, String ageText,
                             long milestoneCount, GrowthRecordResp latestGrowth,
                             MilestoneResp latestMilestone, long growthRecordCount) {
}
