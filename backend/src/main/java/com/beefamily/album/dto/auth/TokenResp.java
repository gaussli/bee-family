package com.beefamily.album.dto.auth;

import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.dto.baby.BabyResp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录 / 注册响应：一次性返回前端引导所需全部上下文。
 *
 * <p>为什么把 babies 也带回来：首登时若为空，前端直接跳「宝宝档案」引导页，
 * 少一次串行请求，避免首页闪一下空态（§6.1 时序图）。</p>
 *
 * @param token      JWT
 * @param expiresAt  token 过期时间
 * @param user       用户信息
 * @param familyId   家庭 id，无家庭时为 null
 * @param familyName 家庭名
 * @param role       家庭角色
 * @param babies     家庭宝宝列表
 */
public record TokenResp(String token, LocalDateTime expiresAt, UserResp user, Long familyId, String familyName,
                        Role role, List<BabyResp> babies) {
}
