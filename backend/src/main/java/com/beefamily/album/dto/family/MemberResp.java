package com.beefamily.album.dto.family;

import com.beefamily.album.domain.enums.Role;
import java.time.LocalDateTime;

/**
 * 家庭成员响应。
 *
 * @param userId            用户 id
 * @param nickname          昵称
 * @param avatarUrl         头像
 * @param email             邮箱
 * @param role              角色
 * @param joinedAt          加入时间
 * @param uploadedPhotoCount 该成员在本家庭上传的照片数（移除成员前的影响面提示）
 */
public record MemberResp(Long userId, String nickname, String avatarUrl, String email, Role role,
                         LocalDateTime joinedAt, long uploadedPhotoCount) {
}
