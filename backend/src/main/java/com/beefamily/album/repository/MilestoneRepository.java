package com.beefamily.album.repository;

import com.beefamily.album.domain.Milestone;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 里程碑仓储。
 *
 * <p>列表与时间轴都以 {@code (family_id, achieved_at DESC, id DESC)} 为骨架，
 * 命中 {@code idx_milestone_family_achieved}。</p>
 */
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    /**
     * 家庭里程碑分页（倒序）。
     *
     * @param familyId 家庭 id
     * @param pageable 分页
     * @return 分页结果
     */
    Page<Milestone> findByFamilyIdOrderByAchievedAtDescIdDesc(Long familyId, Pageable pageable);

    /**
     * 家庭内某宝宝的里程碑分页（倒序）。
     *
     * @param familyId 家庭 id
     * @param babyId   宝宝 id
     * @param pageable 分页
     * @return 分页结果
     */
    Page<Milestone> findByFamilyIdAndBabyIdOrderByAchievedAtDescIdDesc(Long familyId, Long babyId, Pageable pageable);

    /**
     * 某宝宝里程碑（倒序，宝宝详情用）。
     *
     * @param babyId 宝宝 id
     * @return 里程碑列表
     */
    List<Milestone> findByBabyIdOrderByAchievedAtDesc(Long babyId);

    /**
     * 按 id + 家庭查询。
     *
     * @param id       里程碑 id
     * @param familyId 家庭 id
     * @return 里程碑
     */
    Optional<Milestone> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 家庭里程碑数。
     *
     * @param familyId 家庭 id
     * @return 数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 某宝宝里程碑数。
     *
     * @param babyId 宝宝 id
     * @return 数量
     */
    long countByBabyId(Long babyId);

    /**
     * 某宝宝最新里程碑（宝宝详情页）。
     *
     * @param babyId 宝宝 id
     * @return 里程碑
     */
    Optional<Milestone> findFirstByBabyIdOrderByAchievedAtDescIdDesc(Long babyId);

    /**
     * 批量回查（时间轴明细）。
     *
     * @param ids id 集合
     * @return 里程碑列表
     */
    List<Milestone> findAllByIdIn(Collection<Long> ids);

    /**
     * 搜索：标题或描述包含关键词（强制 family 前缀过滤）。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param pageable 分页
     * @return 分页结果
     */
    @Query("select m from Milestone m where m.familyId = :familyId "
            + "and (lower(m.title) like :pattern or lower(m.description) like :pattern) "
            + "order by m.achievedAt desc, m.id desc")
    Page<Milestone> search(@Param("familyId") Long familyId, @Param("pattern") String pattern, Pageable pageable);

    /**
     * 删除家庭全部里程碑（删除家庭第 1 步，先断开 photo/baby 引用）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Milestone m where m.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
