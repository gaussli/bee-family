package com.beefamily.album.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>{@code inviteCode} 是否必填由 service 判定：{@code user} 表为空时（首个用户）忽略该字段并自建家庭；
 * 否则必须有效（40001 无法表达的「业务前置条件」用 40902 更准确）。</p>
 *
 * @param email      登录邮箱
 * @param password   明文密码（6-32 位）
 * @param nickname   显示名（1-32 位）
 * @param inviteCode 邀请码（8 位字母数字，可空）
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
        String nickname,

        @Size(max = 8, message = "邀请码为 8 位字母数字")
        String inviteCode) {
}
