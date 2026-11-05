package com.beefamily.album.repository;

import com.beefamily.album.domain.Photo;
import java.time.LocalDateTime;
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
 * 照片仓储。
 *
 * <p>排序统一在 SQL 层完成：照片网格 {@code ORDER BY taken_at DESC, id DESC}（命中
 * {@code idx_photo_family_taken} / {@code idx_photo_album_taken}），前端不本地排序（§1.3）。</p>
 */
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /**
     * 按 id + 家庭查询。
     *
     * @param id       照片 id
     * @param familyId 家庭 id
     * @return 照片
     */
    Optional<Photo> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 批量按 id + 家庭查询。
     *
     * @param ids      id 集合
     * @param familyId 家庭 id
     * @return 照片列表
     */
    List<Photo> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /**
     * 批量按 id 查询（时间轴明细回查，family 已在排序键查询中过滤）。
     *
     * @param ids id 集合
     * @return 照片列表
     */
    List<Photo> findAllByIdIn(Collection<Long> ids);

    /**
     * 某相册照片分页。
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @param pageable 分页 + 排序
     * @return 分页结果
     */
    Page<Photo> findByFamilyIdAndAlbumId(Long familyId, Long albumId, Pageable pageable);

    /**
     * 未分类照片分页。
     *
     * @param familyId 家庭 id
     * @param pageable 分页 + 排序
     * @return 分页结果
     */
    Page<Photo> findByFamilyIdAndAlbumIdIsNull(Long familyId, Pageable pageable);

    /**
     * 全家庭照片分页（不带 albumId 过滤）。
     *
     * @param familyId 家庭 id
     * @param pageable 分页 + 排序
     * @return 分页结果
     */
    Page<Photo> findByFamilyId(Long familyId, Pageable pageable);

    /**
     * 某相册全部照片（删除相册 photoStrategy=delete 时取 id 与 key）。
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @return 照片列表
     */
    List<Photo> findByFamilyIdAndAlbumId(Long familyId, Long albumId);

    /**
     * 家庭照片总数。
     *
     * @param familyId 家庭 id
     * @return 数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 未分类照片数（相册列表 unclassified 字段）。
     *
     * @param familyId 家庭 id
     * @return 数量
     */
    long countByFamilyIdAndAlbumIdIsNull(Long familyId);

    /**
     * 本月新增照片数（PRD P0-12）。
     *
     * @param familyId 家庭 id
     * @param from     本月 1 日 00:00:00
     * @return 数量
     */
    long countByFamilyIdAndCreatedAtGreaterThanEqual(Long familyId, LocalDateTime from);

    /**
     * 某成员上传的照片数。
     *
     * @param uploadedBy 上传者 user id
     * @param familyId   家庭 id
     * @return 数量
     */
    long countByUploadedByAndFamilyId(Long uploadedBy, Long familyId);

    /**
     * 相册内拍摄时间最新的照片（未设 cover 时兜底，PRD P0-06③）。
     *
     * <p>同时按 {@code id DESC} 兜住同 {@code taken_at} 的稳定序，避免前端封面抖动。</p>
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @return 照片
     */
    Optional<Photo> findFirstByFamilyIdAndAlbumIdOrderByTakenAtDescIdDesc(Long familyId, Long albumId);

    /**
     * 未分类中拍摄时间最新的照片（未分类虚拟相册封面）。
     *
     * @param familyId 家庭 id
     * @return 照片
     */
    Optional<Photo> findFirstByFamilyIdAndAlbumIdIsNullOrderByTakenAtDescIdDesc(Long familyId);

    /**
     * 相册内最早拍摄时间（详情页日期范围）。
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @return 最早拍摄时间
     */
    @Query("select min(p.takenAt) from Photo p where p.familyId = :familyId and p.albumId = :albumId")
    LocalDateTime findMinTakenAt(@Param("familyId") Long familyId, @Param("albumId") Long albumId);

    /**
     * 相册内最晚拍摄时间（详情页日期范围）。
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @return 最晚拍摄时间
     */
    @Query("select max(p.takenAt) from Photo p where p.familyId = :familyId and p.albumId = :albumId")
    LocalDateTime findMaxTakenAt(@Param("familyId") Long familyId, @Param("albumId") Long albumId);

    /**
     * 家庭存储占用（字节）。
     *
     * @param familyId 家庭 id
     * @return 字节数，无照片时为 0
     */
    @Query("select coalesce(sum(p.fileSize), 0) from Photo p where p.familyId = :familyId")
    long sumFileSizeByFamilyId(@Param("familyId") Long familyId);

    /**
     * 搜索：标题 / 描述 / 标签名包含关键词（强制 family 前缀过滤，见 §4.5 Q11）。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param pageable 分页
     * @return 分页结果
     */
    @Query("select p from Photo p where p.familyId = :familyId and ("
            + "lower(p.title) like :pattern or lower(p.description) like :pattern or exists ("
            + "  select pt.id.photoId from PhotoTag pt, Tag t "
            + "  where pt.id.tagId = t.id and pt.id.photoId = p.id and t.familyId = :familyId "
            + "  and lower(t.name) like :pattern)) "
            + "order by p.takenAt desc, p.id desc")
    Page<Photo> search(@Param("familyId") Long familyId, @Param("pattern") String pattern, Pageable pageable);

    /**
     * 相册删除策略 move：显式把照片移到未分类。
     *
     * <p>FK 的 {@code ON DELETE SET NULL} 也能兜底，但显式 UPDATE 才能拿到 {@code movedPhotos} 计数。</p>
     *
     * @param familyId 家庭 id
     * @param albumId  相册 id
     * @return 影响行数
     */
    @Modifying
    @Query("update Photo p set p.albumId = null, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.albumId = :albumId and p.familyId = :familyId")
    int moveToUnclassified(@Param("familyId") Long familyId, @Param("albumId") Long albumId);

    /**
     * 清空指向被删照片的家庭封面引用（{@code family.cover_photo_id} 无 FK）。
     *
     * @param photoIds 被删照片 id 集合
     * @return 影响行数
     */
    @Modifying
    @Query(value = "update family set cover_photo_id = null where cover_photo_id in (:photoIds)", nativeQuery = true)
    int clearFamilyCoverByPhotoIds(@Param("photoIds") Collection<Long> photoIds);

    /**
     * 批量删除照片（限定家庭，防止越权删）。
     *
     * @param ids      id 集合
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Photo p where p.id in :ids and p.familyId = :familyId")
    int deleteByIdInAndFamilyId(@Param("ids") Collection<Long> ids, @Param("familyId") Long familyId);

    /**
     * 删除家庭全部照片（删除家庭第 3 步）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Photo p where p.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);

    /**
     * 家庭全部照片的三档存储 key（删家庭 afterCommit 清磁盘用）。
     *
     * @param familyId 家庭 id
     * @return 每行 (storageKey, thumbKey, previewKey)
     */
    @Query("select p.storageKey, p.thumbKey, p.previewKey from Photo p where p.familyId = :familyId")
    List<Object[]> findStorageKeysByFamilyId(@Param("familyId") Long familyId);
}
