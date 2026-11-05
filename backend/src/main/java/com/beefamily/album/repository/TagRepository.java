package com.beefamily.album.repository;

import com.beefamily.album.domain.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 标签仓储。
 *
 * <p>「输入即搜索」用前缀匹配（{@code LIKE '海%'}）而非 {@code %海%}：只有前缀匹配能走
 * {@code uk_tag_family_name} 索引（§4.5 Q10），且更符合标签补全的交互语义。</p>
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 家庭内按名称精确查（复用既有标签）。
     *
     * @param familyId 家庭 id
     * @param name     标签名
     * @return 标签
     */
    Optional<Tag> findByFamilyIdAndName(Long familyId, String name);

    /**
     * 前缀匹配（标签输入即搜索）。
     *
     * @param familyId 家庭 id
     * @param prefix   前缀
     * @param pageable 限制条数
     * @return 标签列表
     */
    List<Tag> findByFamilyIdAndNameStartingWithOrderByName(Long familyId, String prefix, Pageable pageable);

    /**
     * 家庭内全部标签（冷启动 / 无关键词时的候选）。
     *
     * @param familyId 家庭 id
     * @param pageable 限制条数
     * @return 标签列表
     */
    List<Tag> findByFamilyIdOrderByName(Long familyId, Pageable pageable);

    /**
     * 标签使用情况：照片数 + 最近使用时间。
     *
     * <p>为什么用一条聚合查询：标签列表需要 {@code photoCount} 与 {@code lastUsedAt}，
     * 逐标签 COUNT 会产生 N+1（单家庭标签可达上百个）。LEFT JOIN 保证未使用标签也能出现。</p>
     *
     * @param familyId 家庭 id
     * @param pageable 限制条数
     * @return 每行 (id, name, photoCount, lastUsedAt)
     */
    @Query("select t.id, t.name, count(pt), max(pt.createdAt) from Tag t "
            + "left join PhotoTag pt on pt.id.tagId = t.id "
            + "where t.familyId = :familyId "
            + "group by t.id, t.name "
            + "order by max(pt.createdAt) desc, t.id desc")
    List<Object[]> findUsage(Long familyId, Pageable pageable);

    /**
     * 前缀匹配 + 使用情况（同一聚合，带 {@code like} 过滤）。
     *
     * @param familyId 家庭 id
     * @param prefix   前缀
     * @param pageable 限制条数
     * @return 每行 (id, name, photoCount, lastUsedAt)
     */
    @Query("select t.id, t.name, count(pt), max(pt.createdAt) from Tag t "
            + "left join PhotoTag pt on pt.id.tagId = t.id "
            + "where t.familyId = :familyId and t.name like concat(:prefix, '%') "
            + "group by t.id, t.name "
            + "order by t.name asc")
    List<Object[]> findUsageByPrefix(@Param("familyId") Long familyId, @Param("prefix") String prefix,
                                     Pageable pageable);

    /**
     * 按 id 集合 + 家庭查询。
     *
     * @param ids      id 集合
     * @param familyId 家庭 id
     * @return 标签列表
     */
    List<Tag> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /**
     * 幂等插入标签：已存在则原地不动（只把 {@code id} 赋回自身，MySQL 视为「无变化」，
     * 因此不会触发 {@code updated_at ON UPDATE}，符合「标签行只增不删、不改旧行」的约定）。
     *
     * <p><b>为什么不用「先 insert 再 catch 唯一键冲突」</b>：那条路每次并发都必然产生一次
     * 主键冲突、一次事务回滚，而且 Hibernate 的 {@code SqlExceptionHelper} 会先打一条
     * {@code ERROR Duplicate entry ...} 日志 —— 对「可预期」的并发是纯噪声（实测 10 并发刷 8 条
     * ERROR），还会误导运维告警。{@code ON DUPLICATE KEY UPDATE} 不抛异常、不产生 ERROR 日志。</p>
     *
     * @param familyId 家庭 id
     * @param name     标签名（已规范化）
     * @return 影响行数：1 = 新插入，0 = 已存在
     */
    @Modifying
    @Query(value = "insert into tag (family_id, name, created_at, updated_at) "
            + "values (:familyId, :name, now(), now()) "
            + "on duplicate key update id = last_insert_id(id)", nativeQuery = true)
    int insertIfAbsent(@Param("familyId") Long familyId, @Param("name") String name);

    /**
     * 删除家庭全部标签（删除家庭第 7 步）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Tag t where t.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
