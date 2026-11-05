package com.beefamily.album.mapper;

import com.beefamily.album.domain.Baby;
import com.beefamily.album.dto.baby.BabyDetailResp;
import com.beefamily.album.dto.baby.BabyResp;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.support.DateTimes;

/**
 * 宝宝实体 → DTO。
 *
 * <p>年龄（{@code ageDays} / {@code ageText}）在这里由生日推导：它是「展示派生值」而非持久化字段，
 * 放在 mapper 可保证所有出口（列表 / 详情 / 登录引导）文案完全一致。</p>
 */
public final class BabyMapper {

    private BabyMapper() {
    }

    /**
     * 转宝宝响应。
     *
     * @param baby           宝宝实体
     * @param milestoneCount 里程碑数
     * @param latestGrowth   最新成长记录
     * @return 宝宝响应
     */
    public static BabyResp toResp(Baby baby, long milestoneCount, GrowthRecordResp latestGrowth) {
        if (baby == null) {
            return null;
        }
        return new BabyResp(baby.getId(), baby.getName(), baby.getGender(), baby.getBirthday(), baby.getAvatarUrl(),
                baby.getBirthHeightCm(), baby.getBirthWeightKg(), DateTimes.ageDays(baby.getBirthday()),
                DateTimes.ageText(baby.getBirthday()), milestoneCount, latestGrowth);
    }

    /**
     * 转宝宝详情响应。
     *
     * @param baby              宝宝实体
     * @param milestoneCount    里程碑数
     * @param latestGrowth      最新成长记录
     * @param latestMilestone   最新里程碑
     * @param growthRecordCount 成长记录条数
     * @return 宝宝详情响应
     */
    public static BabyDetailResp toDetailResp(Baby baby, long milestoneCount, GrowthRecordResp latestGrowth,
                                              MilestoneResp latestMilestone, long growthRecordCount) {
        if (baby == null) {
            return null;
        }
        return new BabyDetailResp(baby.getId(), baby.getName(), baby.getGender(), baby.getBirthday(),
                baby.getAvatarUrl(), baby.getBirthHeightCm(), baby.getBirthWeightKg(),
                DateTimes.ageDays(baby.getBirthday()), DateTimes.ageText(baby.getBirthday()),
                milestoneCount, latestGrowth, latestMilestone, growthRecordCount);
    }
}
