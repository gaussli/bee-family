package com.beefamily.album.repository;

import com.beefamily.album.domain.Family;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 家庭仓储。
 *
 * <p>家庭是权限树的根，按 id 直查不存在跨家庭风险；子资源一律带 {@code familyId} 查询。</p>
 */
public interface FamilyRepository extends JpaRepository<Family, Long> {

    /**
     * 按邀请码查询。
     *
     * @param inviteCode 8 位邀请码
     * @return 家庭
     */
    Optional<Family> findByInviteCode(String inviteCode);

    /**
     * 邀请码是否已被占用（生成新码时避免撞唯一索引）。
     *
     * @param inviteCode 邀请码
     * @return true 表示已占用
     */
    boolean existsByInviteCode(String inviteCode);
}
