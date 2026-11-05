package com.beefamily.album.repository;

import com.beefamily.album.domain.GrowthRecord;
import java.time.LocalDate;
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
 * 成长记录仓储。
 *
 * <p>UPSERT 依赖 {@code uk_growth_baby_date}：service 先按 {@code (babyId, recordedAt)} 查既有行，
 * 命中则覆盖测量值（不抛 409），未命中则新增。</p>
 *
 * <p>成长曲线查询区间来自「该宝宝最新一条记录」而非今天（PRD 语义），因此需要
 * {@link #findFirstByBabyIdOrderByRecordedAtDesc(Long)}。</p>
 */
public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {

    /**
     * 按 id + 宝宝查询。
     *
     * @param id     记录 id
     * @param babyId 宝宝 id
     * @return 记录
     */
    Optional<GrowthRecord> findByIdAndBabyId(Long id, Long babyId);

    /**
     * 按 id + 家庭查询（跨家庭统一 40301，service 层不需要先查宝宝）。
     *
     * @param id       记录 id
     * @param familyId 家庭 id
     * @return 记录
     */
    @Query("select g from GrowthRecord g where g.id = :id "
            + "and g.babyId in (select b.id from Baby b where b.familyId = :familyId)")
    Optional<GrowthRecord> findByIdAndFamilyId(@Param("id") Long id, @Param("familyId") Long familyId);

    /**
     * 某宝宝记录分页（倒序）。
     *
     * @param babyId   宝宝 id
     * @param pageable 分页
     * @return 分页结果
     */
    Page<GrowthRecord> findByBabyIdOrderByRecordedAtDesc(Long babyId, Pageable pageable);

    /**
     * 区间查询（升序，供成长曲线使用）。
     *
     * @param babyId 宝宝 id
     * @param from   起始日期
     * @param to     结束日期
     * @return 记录列表
     */
    List<GrowthRecord> findByBabyIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long babyId, LocalDate from, LocalDate to);

    /**
     * 全部记录（升序，{@code range=all} 使用）。
     *
     * @param babyId 宝宝 id
     * @return 记录列表
     */
    List<GrowthRecord> findByBabyIdOrderByRecordedAtAsc(Long babyId);

    /**
     * 最新一条记录（BabyResp.latestGrowth 与曲线区间起点）。
     *
     * @param babyId 宝宝 id
     * @return 记录
     */
    Optional<GrowthRecord> findFirstByBabyIdOrderByRecordedAtDesc(Long babyId);

    /**
     * 按宝宝 + 日期查既有记录（UPSERT 判定）。
     *
     * @param babyId     宝宝 id
     * @param recordedAt 测量日期
     * @return 记录
     */
    Optional<GrowthRecord> findByBabyIdAndRecordedAt(Long babyId, LocalDate recordedAt);

    /**
     * 某宝宝记录数。
     *
     * @param babyId 宝宝 id
     * @return 数量
     */
    long countByBabyId(Long babyId);

    /**
     * 批量回查（时间轴明细）。
     *
     * @param ids id 集合
     * @return 记录列表
     */
    List<GrowthRecord> findAllByIdIn(Collection<Long> ids);

    /**
     * 删除某宝宝的记录（删除宝宝 / 删除家庭）。
     *
     * @param babyIds 宝宝 id 集合
     * @return 影响行数
     */
    @Modifying
    @Query("delete from GrowthRecord g where g.babyId in :babyIds")
    int deleteByBabyIdIn(@Param("babyIds") Collection<Long> babyIds);

    /**
     * 删除家庭全部成长记录（删除家庭第 4 步，按家庭经 baby 子查询定位）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from GrowthRecord g where g.babyId in (select b.id from Baby b where b.familyId = :familyId)")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
