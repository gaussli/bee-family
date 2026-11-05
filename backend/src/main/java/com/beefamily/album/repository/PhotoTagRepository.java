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
     * 统计某标签关联的照片数。
     *
     * @param tagId 标签 id
     * @return 数量
     */
    long countByIdTagId(Long tagId);
}
