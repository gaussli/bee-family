package com.beefamily.album.dto.auth;

import java.time.LocalDateTime;

/**
 * 用户响应。
 *
 * @param id        用户 id
 * @param email     邮箱
 * @param nickname  显示名
 * @param avatarUrl 头像地址
 * @param createdAt 注册时间
 */
public record UserResp(Long id, String email, String nickname, String avatarUrl, LocalDateTime createdAt) {
}
