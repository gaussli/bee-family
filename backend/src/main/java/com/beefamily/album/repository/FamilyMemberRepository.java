package com.beefamily.album.repository;

import com.beefamily.album.domain.FamilyMember;
import com.beefamily.album.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 家庭成员仓储。
 *
 * <p>{@link #findByUserId(Long)} 是每个认证请求都会执行的查询，命中 {@code uk_member_user}（&lt;0.2ms），
 * 也是「familyId 不进 JWT」这一决策的成本依据（ADR-02）。</p>
 */
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    /**
     * 按用户查家庭关系（1 用户 = 1 家庭，唯一索引保证）。
     *
     * @param userId 用户 id
     * @return 成员关系
     */
    Optional<FamilyMember> findByUserId(Long userId);

    /**
     * 家庭全部成员，按加入时间升序。
     *
     * @param familyId 家庭 id
     * @return 成员列表
     */
    List<FamilyMember> findByFamilyIdOrderByJoinedAt(Long familyId);

    /**
     * 家庭成员数。
     *
     * @param familyId 家庭 id
     * @return 成员数
     */
    long countByFamilyId(Long familyId);

    /**
     * 指定用户是否属于该家庭。
     *
     * @param familyId 家庭 id
     * @param userId   用户 id
     * @return true 表示属于
     */
    boolean existsByFamilyIdAndUserId(Long familyId, Long userId);

    /**
     * 查询指定用户在该家庭中的角色。
     *
     * @param familyId 家庭 id
     * @param userId   用户 id
     * @return 角色
     */
    @Query("select m.role from FamilyMember m where m.familyId = :familyId and m.userId = :userId")
    Optional<Role> findRoleByFamilyIdAndUserId(@Param("familyId") Long familyId, @Param("userId") Long userId);

    /**
     * 按家庭 + 用户定位成员行（移除成员用）。
     *
     * @param familyId 家庭 id
     * @param userId   用户 id
     * @return 成员关系
     */
    Optional<FamilyMember> findByFamilyIdAndUserId(Long familyId, Long userId);

    /**
     * 删除某成员关系（不删用户、不删照片）。
     *
     * @param familyId 家庭 id
     * @param userId   用户 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from FamilyMember m where m.familyId = :familyId and m.userId = :userId")
    int deleteMember(@Param("familyId") Long familyId, @Param("userId") Long userId);

    /**
     * 删除家庭全部成员关系（删除家庭第 8 步）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from FamilyMember m where m.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
