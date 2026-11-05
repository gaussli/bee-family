package com.beefamily.album.dto.photo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量删除请求。
 *
 * @param photoIds 照片 id 列表（1-100）
 */
public record BatchDeleteReq(
        @NotEmpty(message = "不能为空")
        @Size(max = 100, message = "单批最多 100 张")
        List<Long> photoIds) {
}
