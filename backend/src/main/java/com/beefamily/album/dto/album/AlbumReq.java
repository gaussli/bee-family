package com.beefamily.album.dto.album;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建相册请求。
 *
 * @param name        相册名（去首尾空格后 1-40 字，家庭内唯一）
 * @param description 描述 ≤300
 */
public record AlbumReq(
        @NotBlank(message = "不能为空")
        @Size(max = 40, message = "长度不能超过 40 个字符")
        String name,

        @Size(max = 300, message = "长度不能超过 300 个字符")
        String description) {
}
