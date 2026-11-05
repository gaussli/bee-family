package com.beefamily.album.dto.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改家庭名请求。
 *
 * @param name 家庭名（1-40 字）
 */
public record UpdateFamilyReq(
        @NotBlank(message = "不能为空")
        @Size(max = 40, message = "长度不能超过 40 个字符")
        String name) {
}
