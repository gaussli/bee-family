package com.beefamily.album.repository;

import com.beefamily.album.domain.Baby;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 宝宝档案仓储。
 *
 * <p>所有查询强制带 {@code familyId}：跨家庭访问在 service 层转为 40301（§7.5 第 1 层防线）。</p>
 */
public interface BabyRepository extends JpaRepository<Baby, Long> {

    /**
     * 家庭内宝宝列表（按 id 升序，稳定序）。
     *
     * @param familyId 家庭 id
     * @return 宝宝列表
     */
    List<Baby> findByFamilyIdOrderById(Long familyId);

    /**
     * 按 id + 家庭查询。
     *
     * @param id       宝宝 id
     * @param familyId 家庭 id
     * @return 宝宝
     */
    Optional<Baby> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 批量查询（时间轴明细回查用）。
     *
     * @param ids      id 集合
     * @param familyId 家庭 id
     * @return 宝宝列表
     */
    List<Baby> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /**
     * 家庭宝宝数量（上限 3 校验）。
     *
     * @param familyId 家庭 id
     * @return 数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 删除家庭全部宝宝（删除家庭第 6 步）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Baby b where b.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
