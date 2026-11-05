package com.beefamily.album.mapper;

import com.beefamily.album.domain.User;
import com.beefamily.album.dto.auth.UserResp;

/**
 * 用户实体 → DTO。
 *
 * <p>mapper 只做字段搬运：不注入 Spring、不查库、不含业务判断（§3.2）。</p>
 */
public final class UserMapper {

    private UserMapper() {
    }

    /**
     * 转用户响应。
     *
     * @param user 用户实体
     * @return 用户响应，入参为 null 时返回 null
     */
    public static UserResp toResp(User user) {
        if (user == null) {
            return null;
        }
        return new UserResp(user.getId(), user.getEmail(), user.getNickname(), user.getAvatarUrl(),
                user.getCreatedAt());
    }
}
