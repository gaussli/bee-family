package com.beefamily.album.dto.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 删除家庭请求（危险操作二次确认）。
 *
 * <p>必须与家庭名完全一致才允许删除（40908）；不匹配时不做任何删除动作。</p>
 *
 * @param confirmName 用户输入的家庭名
 */
public record DeleteFamilyReq(
        @NotBlank(message = "不能为空")
        @Size(max = 40, message = "长度不能超过 40 个字符")
        String confirmName) {
}
