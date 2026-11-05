package com.beefamily.album.repository;

import com.beefamily.album.domain.PhotoTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 照片-标签关联仓储。
 *
 * <p>全部方法以批量（{@code IN}）为基础：标签回写与照片删除都不允许在循环里逐条 SQL（N+1 禁令）。</p>
 */
public interface PhotoTagRepository extends JpaRepository<PhotoTag, com.beefamily.album.domain.PhotoTagId> {

    /**
     * 批量查询照片的标签关联。
     *
     * @param photoIds 照片 id 集合
     * @return 关联列表
     */
    @Query("select pt from PhotoTag pt where pt.id.photoId in :photoIds")
    List<PhotoTag> findByPhotoIdIn(@Param("photoIds") Collection<Long> photoIds);

    /**
     * 批量删除照片的标签关联。
     *
     * @param photoIds 照片 id 集合
     * @return 影响行数
     */
    @Modifying
    @Query("delete from PhotoTag pt where pt.id.photoId in :photoIds")
    int deleteByPhotoIdIn(@Param("photoIds") Collection<Long> photoIds);

    /**
     * 删除家庭全部照片的标签关联（删除家庭第 2 步，经 photo 子查询定位）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from PhotoTag pt where pt.id.photoId in (select p.id from Photo p where p.familyId = :familyId)")
    int deleteByFamilyId(@Param("familyId") Long familyId);

    /**
     * 幂等插入一条照片-标签关联。
     *
     * <p><b>为什么不用 {@code saveAll}</b>：同一张照片被两个请求同时打同一个标签时，
     * 联合主键 {@code (photo_id, tag_id)} 会冲突，`saveAll` 直接抛
     * {@code DataIntegrityViolationException} → 500。而对「打标签」这个动作来说，
     * 并发写同一个关联本来就应该收敛成一行，冲突是**正常**语义，不该用异常表达。</p>
     *
     * <p><b>为什么用 {@code ON DUPLICATE KEY UPDATE} 而不是 {@code INSERT IGNORE}</b>：
     * {@code INSERT IGNORE} 会把**所有**错误降级成 warning，包括外键不存在（tag 刚被删）——
     * 那会静默丢关联。{@code ON DUPLICATE KEY UPDATE} 只吞唯一键冲突，外键错误照抛。</p>
     *
     * @param photoId 照片 id
     * @param tagId   标签 id
     * @return 影响行数：1 = 新插入，0 = 已存在（MySQL 对「无实际变更」返回 0）
     */
    @Modifying
    @Query(value = "insert into photo_tag (photo_id, tag_id, created_at) values (:photoId, :tagId, now()) "
            + "on duplicate key update photo_id = photo_id", nativeQuery = true)
    int insertIfAbsent(@Param("photoId") Long photoId, @Param("tagId") Long tagId);

    /**
     * 统计某标签关联的照片数。
     *
     * @param tagId 标签 id
     * @return 数量
     */
    long countByIdTagId(Long tagId);
}
