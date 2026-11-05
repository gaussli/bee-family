package com.beefamily.album.dto.album;

import jakarta.validation.constraints.Size;

/**
 * 更新相册请求（全量提交语义）。
 *
 * <p>{@code coverPhotoId} 为 null 表示「清除自定义封面，回到取相册内最新照片」的兜底逻辑；
 * 非 null 时必须是**本相册内**的照片（否则 40301 / 40907）。</p>
 *
 * @param name         相册名
 * @param description  描述
 * @param coverPhotoId 封面照片 id
 */
public record AlbumUpdateReq(
        @Size(min = 1, max = 40, message = "长度需为 1-40 个字符")
        String name,

        @Size(max = 300, message = "长度不能超过 300 个字符")
        String description,

        Long coverPhotoId) {
}
