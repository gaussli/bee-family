package com.beefamily.album.repository;

import com.beefamily.album.domain.Family;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 家庭仓储。
 *
 * <p>家庭是权限树的根，按 id 直查不存在跨家庭风险；子资源一律带 {@code familyId} 查询。</p>
 *
 * <p><b>Q7 旧邀请码已整体移除</b>：{@code findByInviteCode} / {@code existsByInviteCode}
 * 随 V2 的 {@code invite_code} 列一起删除，无别名、无弃用接口、无双份基线。
 * v1.1 的 {@code family_code} 查询由 T02 补齐 {@link Family#getName()} 同批的
 * {@code familyCode} 字段后以 {@code findByFamilyCode} / {@code existsByFamilyCode} 提供；
 * T01 不实现任何新业务。</p>
 */
public interface FamilyRepository extends JpaRepository<Family, Long> {
}
