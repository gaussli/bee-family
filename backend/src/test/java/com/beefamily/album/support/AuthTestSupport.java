package com.beefamily.album.support;

import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.security.UserPrincipal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 单元测试用的认证上下文助手。
 *
 * <p>{@code SecurityUtils} 只读 {@code SecurityContextHolder}，service 层测试必须先塞入主体，
 * 否则 {@code currentUserId()} 会抛 40100。这里集中一处，避免每个测试重复写 SecurityContext 样板。</p>
 */
public final class AuthTestSupport {

    /** 测试家庭 id。 */
    public static final Long FAMILY_ID = 100L;

    private AuthTestSupport() {
        // 工具类禁止实例化
    }

    /**
     * 以 owner 身份登录。
     *
     * @param userId 用户 id
     */
    public static void loginAsOwner(long userId) {
        login(userId, Role.owner);
    }

    /**
     * 以普通成员身份登录。
     *
     * @param userId 用户 id
     */
    public static void loginAsMember(long userId) {
        login(userId, Role.member);
    }

    /**
     * 登录为指定角色。
     *
     * @param userId 用户 id
     * @param role   角色
     */
    public static void login(long userId, Role role) {
        UserPrincipal principal = new UserPrincipal(userId, "qa@home.local", "严过关", FAMILY_ID, role);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 清空认证上下文。
     */
    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
