package com.beefamily.album.security;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前主体读取工具。
 *
 * <p>只读 {@code SecurityContextHolder}，不做任何鉴权判断（判断统一在 {@link FamilyAccessGuard}），
 * 避免「两处各写一套权限规则」后逐渐不一致。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 工具类禁止实例化
    }

    /**
     * 当前主体（可能为空）。
     *
     * @return 主体
     */
    public static Optional<UserPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }

    /**
     * 当前主体（未认证抛 40100）。
     *
     * @return 主体
     */
    public static UserPrincipal requirePrincipal() {
        return current().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 当前用户 id（未认证抛 40100）。
     *
     * @return 用户 id
     */
    public static Long currentUserId() {
        return requirePrincipal().userId();
    }
}
