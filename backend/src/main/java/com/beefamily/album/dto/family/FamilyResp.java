package com.beefamily.album.dto.family;

import java.time.LocalDateTime;

/**
 * 家庭信息响应。
 *
 * <p>{@code inviteCode} / {@code inviteCodeExpiresAt} 仅对 owner 返回，member 拿到 null
 * （前端据此不渲染邀请区，属 UI 便利，不作为安全边界）。</p>
 *
 * @param id                  家庭 id
 * @param name                家庭名
 * @param ownerId             创建者 user id
 * @param ownerNickname       创建者昵称
 * @param memberCount         当前成员数
 * @param memberLimit         成员上限（固定 6）
 * @param coverPhotoId        家庭封面照片 id
 * @param inviteCode          邀请码（仅 owner）
 * @param inviteCodeExpiresAt 邀请码过期时间（仅 owner）
 * @param createdAt           创建时间
 */
public record FamilyResp(Long id, String name, Long ownerId, String ownerNickname, int memberCount, int memberLimit,
                         Long coverPhotoId, String inviteCode, LocalDateTime inviteCodeExpiresAt,
                         LocalDateTime createdAt) {
}
