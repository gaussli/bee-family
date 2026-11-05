package com.beefamily.album.repository;

import com.beefamily.album.domain.Album;
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
 * 相册仓储。
 *
 * <p>{@code photoCount} 只读：写入由 {@code PhotoService} 在同一事务内维护（T09 关键约束①），
 * 因此这里不提供「重算计数」的方法，避免两条写路径互相覆盖。</p>
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    /**
     * 家庭相册列表（按 updated_at 倒序，PRD P0-06④）。
     *
     * @param familyId 家庭 id
     * @return 相册列表
     */
    List<Album> findByFamilyIdOrderByUpdatedAtDesc(Long familyId);

    /**
     * 按 id + 家庭查询。
     *
     * @param id       相册 id
     * @param familyId 家庭 id
     * @return 相册
     */
    Optional<Album> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 批量按 id + 家庭查询（照片列表补相册名，避免 N+1）。
     *
     * @param ids      id 集合
     * @param familyId 家庭 id
     * @return 相册列表
     */
    List<Album> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /**
     * 家庭内是否已有同名相册。
     *
     * @param familyId 家庭 id
     * @param name     相册名（已去首尾空格）
     * @return true 表示重名
     */
    boolean existsByFamilyIdAndName(Long familyId, String name);

    /**
     * 按家庭 + 名称查询。
     *
     * @param familyId 家庭 id
     * @param name     相册名
     * @return 相册
     */
    Optional<Album> findByFamilyIdAndName(Long familyId, String name);

    /**
     * 找出把某张照片设为封面的相册（删照片时需清引用，cover 列无 FK）。
     *
     * @param familyId 家庭 id
     * @param photoId  照片 id
     * @return 相册
     */
    Optional<Album> findFirstByFamilyIdAndCoverPhotoId(Long familyId, Long photoId);

    /**
     * 清空指向被删照片的相册封面引用。
     *
     * <p>为什么必须显式清：{@code album.cover_photo_id} 没有外键（§4.3 方案 B），
     * 删照片后 DB 不会自动置 NULL，会留下悬挂引用。</p>
     *
     * @param photoIds 被删照片 id 集合
     * @return 影响行数
     */
    @Modifying
    @Query("update Album a set a.coverPhotoId = null where a.coverPhotoId in :photoIds")
    int clearCoverByPhotoIds(@Param("photoIds") Collection<Long> photoIds);

    /**
     * 相册总数。
     *
     * @param familyId 家庭 id
     * @return 数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 搜索：相册名包含关键词。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param pageable 分页
     * @return 分页结果
     */
    @Query("select a from Album a where a.familyId = :familyId and lower(a.name) like :pattern "
            + "order by a.updatedAt desc, a.id desc")
    Page<Album> search(@Param("familyId") Long familyId, @Param("pattern") String pattern, Pageable pageable);

    /**
     * 删除家庭全部相册（删除家庭第 5 步，此时照片已删）。
     *
     * @param familyId 家庭 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from Album a where a.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") Long familyId);
}
