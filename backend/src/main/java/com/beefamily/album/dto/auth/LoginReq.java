package com.beefamily.album.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param email    邮箱
 * @param password 明文密码
 */
public record LoginReq(
        @NotBlank(message = "不能为空")
        @Size(max = 120, message = "长度不能超过 120 个字符")
        String email,

        @NotBlank(message = "不能为空")
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String password) {
}
