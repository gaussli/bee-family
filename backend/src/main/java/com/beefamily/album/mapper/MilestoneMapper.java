package com.beefamily.album.mapper;

import com.beefamily.album.domain.Milestone;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.support.DateTimes;
import java.time.LocalDate;

/**
 * 里程碑实体 → DTO。
 */
public final class MilestoneMapper {

    private MilestoneMapper() {
    }

    /**
     * 转里程碑响应。
     *
     * @param milestone      里程碑实体
     * @param babyName       宝宝昵称（家庭级里程碑为 null）
     * @param babyBirthday   宝宝生日（用于计算达成时年龄；无宝宝时 null）
     * @param photoThumbKey  配图缩略图 key
     * @param createdByName  创建者昵称
     * @return 响应
     */
    public static MilestoneResp toResp(Milestone milestone, String babyName, LocalDate babyBirthday,
                                       String photoThumbKey, String createdByName) {
        if (milestone == null) {
            return null;
        }
        String ageText = babyBirthday == null
                ? null
                : DateTimes.ageTextAt(babyBirthday, milestone.getAchievedAt());
        return new MilestoneResp(milestone.getId(), milestone.getBabyId(), babyName, milestone.getTitle(),
                milestone.getDescription(), milestone.getAchievedAt(), ageText, milestone.getPhotoId(),
                photoThumbKey, milestone.getCreatedBy(), createdByName, milestone.getCreatedAt());
    }
}
