package com.beefamily.album.mapper;

import com.beefamily.album.domain.Family;
import com.beefamily.album.domain.FamilyMember;
import com.beefamily.album.domain.User;
import com.beefamily.album.dto.family.FamilyResp;
import com.beefamily.album.dto.family.InviteCodeResp;
import com.beefamily.album.dto.family.MemberResp;
import com.beefamily.album.support.DateTimes;
import com.beefamily.album.support.Limits;

/**
 * 家庭 / 成员实体 → DTO。
 */
public final class FamilyMapper {

    private FamilyMapper() {
    }

    /**
     * 转家庭响应。
     *
     * @param family          家庭实体
     * @param ownerNickname   创建者昵称
     * @param memberCount     成员数
     * @param includeInviteCode 是否返回邀请码（仅 owner）
     * @return 家庭响应
     */
    public static FamilyResp toResp(Family family, String ownerNickname, long memberCount, boolean includeInviteCode) {
        if (family == null) {
            return null;
        }
        return new FamilyResp(family.getId(), family.getName(), family.getOwnerId(), ownerNickname,
                (int) memberCount, Limits.FAMILY_MEMBER_LIMIT, family.getCoverPhotoId(),
                includeInviteCode ? family.getInviteCode() : null,
                includeInviteCode ? family.getInviteCodeExpiresAt() : null,
                family.getCreatedAt());
    }

    /**
     * 转成员响应。
     *
     * @param member            成员关系
     * @param user              用户实体
     * @param uploadedPhotoCount 该成员上传照片数
     * @return 成员响应
     */
    public static MemberResp toMemberResp(FamilyMember member, User user, long uploadedPhotoCount) {
        return new MemberResp(member.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                user == null ? null : user.getEmail(),
                member.getRole(), member.getJoinedAt(), uploadedPhotoCount);
    }

    /**
     * 转邀请码响应。
     *
     * @param family 家庭实体
     * @return 邀请码响应
     */
    public static InviteCodeResp toInviteCodeResp(Family family) {
        boolean expired = family.getInviteCodeExpiresAt() == null
                || family.getInviteCodeExpiresAt().isBefore(DateTimes.now());
        return new InviteCodeResp(family.getInviteCode(), family.getInviteCodeExpiresAt(), expired);
    }
}
