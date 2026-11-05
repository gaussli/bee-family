package com.beefamily.album.dto.auth;

import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.dto.baby.BabyResp;
import com.beefamily.album.dto.family.FamilyResp;
import java.util.List;

/**
 * 当前登录用户信息。
 *
 * <p>{@code family} 可能为 null：被移出家庭的用户仍可登录并看到提示，但所有家庭内接口会返回 40300。</p>
 *
 * @param user   用户信息
 * @param family 家庭信息
 * @param role   家庭角色
 * @param babies 宝宝列表
 */
public record MeResp(UserResp user, FamilyResp family, Role role, List<BabyResp> babies) {
}
