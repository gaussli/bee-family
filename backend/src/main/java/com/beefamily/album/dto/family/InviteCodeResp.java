package com.beefamily.album.dto.family;

import java.time.LocalDateTime;

/**
 * 邀请码响应。
 *
 * @param code      8 位邀请码
 * @param expiresAt 过期时间
 * @param expired   是否已过期（服务端判定，前端直接用）
 */
public record InviteCodeResp(String code, LocalDateTime expiresAt, boolean expired) {
}
