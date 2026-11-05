package com.beefamily.album.security;

import com.beefamily.album.domain.enums.Role;
import java.io.Serializable;

/**
 * 当前登录主体（只放标量，禁止放实体对象）。
 *
 * <p>{@code familyId} / {@code role} 由 {@code JwtAuthenticationFilter} 每次请求从
 * {@code family_member} 解析（ADR-02）：不进 JWT，保证「被移除成员 / 家庭重建」即时生效。</p>
 *
 * <p>{@code familyId} 可能为 null：用户被移出家庭后仍能登录（用于展示提示），但访问家庭内接口
 * 会被 {@code FamilyAccessGuard#familyId()} 判为 40300。</p>
 *
 * @param userId   用户 id
 * @param email    登录邮箱
 * @param nickname 显示名
 * @param familyId 所属家庭 id，可为 null
 * @param role     家庭角色，可为 null
 */
public record UserPrincipal(Long userId, String email, String nickname, Long familyId, Role role) implements Serializable {

    private static final long serialVersionUID = 1L;
}
