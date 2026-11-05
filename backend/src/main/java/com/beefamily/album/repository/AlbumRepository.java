package com.beefamily.album.repository;

import com.beefamily.album.domain.Album;
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
     * 原子加减相册冗余计数（唯一写入口）。
     *
     * <p><b>为什么必须是单条 UPDATE，而不是「读出来 +1 再 save」</b>：上传事务里原本是
     * 先 {@code INSERT photo}（InnoDB 为校验外键会对 album 父行加共享锁）、再
     * {@code findById + setPhotoCount + save}（Hibernate 会整行 UPDATE，需要把共享锁升级成排他锁）。
     * 两个并发上传各自持有共享锁又都要升级，就互等成死锁 —— 实测同一相册 3 并发 × 5 轮，
     * 15 次上传失败 9 次，栈是
     * {@code Deadlock found when trying to get lock ... update album set cover_photo_id=?,...,photo_count=? where id=?}。
     * 改成单条自增 SQL 后既没有「读-改-写」（不会丢更新），也不会顺带重写 name/description/cover 整行。</p>
     *
     * <p><b>负零守卫</b>：{@code photo_count + :delta >= 0} 保证任何调用顺序都不会把计数减成负数；
     * 命中 0 行时由调用方区分「相册不存在」与「已经减到 0」。</p>
     *
     * <p><b>为什么显式写 updated_at</b>：JPQL 批量更新不会触发实体的 {@code @PreUpdate}，
     * 而相册列表按 {@code updated_at} 倒序（PRD P0-06④），必须手动带上。</p>
     *
     * @param albumId   相册 id
     * @param familyId  家庭 id（跨家庭的相册不会被命中）
     * @param delta     增量（正数加、负数减）
     * @param updatedAt 新的更新时间
     * @return 影响行数；0 表示相册不存在 / 不属于该家庭 / 减法会把计数减成负数
     */
    @Modifying
    @Query("update Album a set a.photoCount = a.photoCount + :delta, a.updatedAt = :updatedAt "
            + "where a.id = :albumId and a.familyId = :familyId and a.photoCount + :delta >= 0")
    int addPhotoCount(@Param("albumId") Long albumId, @Param("familyId") Long familyId,
                      @Param("delta") int delta, @Param("updatedAt") LocalDateTime updatedAt);

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
