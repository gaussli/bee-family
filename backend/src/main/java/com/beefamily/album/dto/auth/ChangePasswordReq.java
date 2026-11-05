package com.beefamily.album.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码（6-32 位）
 */
public record ChangePasswordReq(
        @NotBlank(message = "不能为空")
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String oldPassword,

        @NotBlank(message = "不能为空")
        @Size(min = 6, max = 32, message = "长度需为 6-32 个字符")
        String newPassword) {
}
