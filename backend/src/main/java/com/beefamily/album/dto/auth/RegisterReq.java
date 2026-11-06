package com.beefamily.album.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求（v1.1：Q1 无任何码）。
 *
 * <p>Q7 已移除旧邀请码：注册不再携带 {@code inviteCode}，也不再凭码加入家庭。
 * 注册后的家庭归属（0 家庭引导 / 凭家庭码申请）由 T02 / T03 按
 * {@code docs/07-architecture-incremental.md} §5.3.1 实现；本 DTO 只保留账号本身的三个字段。</p>
 *
 * @param email    登录邮箱
 * @param password 明文密码（6-32 位）
 * @param nickname 显示名（1-32 位）
 */
public record RegisterReq(
        @NotBlank(message = "不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "长度不能超过 120 个字符")
        String email,

        @NotBlank(message = "不能为空")
        @Size(min = 6, max = 32, message = "长度需为 6-32 个字符")
        String password,

        @NotBlank(message = "不能为空")
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String nickname) {
}
