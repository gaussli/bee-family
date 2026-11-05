package com.beefamily.album.dto.auth;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料请求（全量提交语义，见 §11.4）。
 *
 * @param nickname  显示名，null 表示不修改（列 NOT NULL，无法置空）
 * @param avatarUrl 头像地址，null 表示不修改
 */
public record UpdateProfileReq(
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String nickname,

        @Size(max = 255, message = "长度不能超过 255 个字符")
        String avatarUrl) {
}
