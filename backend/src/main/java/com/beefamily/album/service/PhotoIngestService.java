package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.domain.enums.MediaType;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.support.DateTimes;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单张照片入库（独立事务）。
 *
 * <p><b>为什么必须拆成独立 Bean</b>：{@code PhotoService} 里的 {@code this.ingestOne()} 不会走 Spring 代理，
 * {@code @Transactional} 会静默失效（§3.5 自调用陷阱）。拆成独立 Bean 后每张照片一个
 * {@code REQUIRES_NEW} 事务：一张失败只回滚自己，其余 99 张照常入库，符合 PRD P0-07「失败重试」语义。</p>
 *
 * <p><b>为什么不用一个大事务</b>：100 张一个事务时任一失败会全回滚；且长事务持有连接与锁，
 * 会拖慢并发的浏览请求。</p>
 */
@Service
public class PhotoIngestService {

    private static final Logger log = LoggerFactory.getLogger(PhotoIngestService.class);

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;

    /**
     * 构造服务。
     *
     * @param photoRepository 照片仓储
     * @param albumRepository 相册仓储（维护冗余计数）
     */
    public PhotoIngestService(PhotoRepository photoRepository, AlbumRepository albumRepository) {
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
    }

    /**
     * 写入照片记录，并在**同一事务内**维护相册冗余计数。
     *
     * <p><b>顺序很重要</b>：先做相册计数的原子 UPDATE（拿到 album 行的排他锁），再 INSERT photo。
     * 反过来写（先 INSERT，靠外键校验拿到 album 的共享锁，再改计数升级成排他锁）在并发上传时
     * 会稳定死锁，详见 {@link AlbumRepository#addPhotoCount}。同一事务内顺序颠倒不会破坏语义：
     * 任何一步失败都会整体回滚。</p>
     *
     * @param draft 照片草稿（文件已落盘）
     * @return 已入库的照片实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Photo ingest(PhotoDraft draft) {
        if (draft.albumId() != null) {
            int affected = albumRepository.addPhotoCount(draft.albumId(), draft.familyId(), 1, DateTimes.now());
            if (affected == 0) {
                // 相册不存在 / 不属于本家庭 / 已被并发删除：不能静默吞掉，否则照片会挂在已删相册上
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标相册不存在或已被删除");
            }
        }

        Photo photo = new Photo();
        photo.setFamilyId(draft.familyId());
        photo.setAlbumId(draft.albumId());
        photo.setStorageKey(draft.storageKey());
        photo.setThumbKey(draft.thumbKey());
        photo.setPreviewKey(draft.previewKey());
        photo.setMimeType(draft.mimeType());
        photo.setFileSize(draft.fileSize());
        photo.setWidth(draft.width());
        photo.setHeight(draft.height());
        photo.setTakenAt(draft.takenAt());
        photo.setMediaType(MediaType.photo);
        photo.setUploadedBy(draft.uploadedBy());
        Photo saved = photoRepository.save(photo);

        log.debug("photo ingested id={} familyId={} albumId={}", saved.getId(), saved.getFamilyId(),
                saved.getAlbumId());
        return saved;
    }

    /**
     * 照片草稿：文件已落盘、元数据已解析，等待入库。
     *
     * @param familyId   家庭 id
     * @param albumId    相册 id（null = 未分类）
     * @param storageKey 原图 key
     * @param thumbKey   缩略图 key
     * @param previewKey 预览图 key
     * @param mimeType   MIME
     * @param fileSize   字节数
     * @param width      宽（无法解码时为 null）
     * @param height     高（无法解码时为 null）
     * @param takenAt    拍摄时间
     * @param uploadedBy 上传者 user id
     */
    public record PhotoDraft(Long familyId, Long albumId, String storageKey, String thumbKey, String previewKey,
                             String mimeType, long fileSize, Integer width, Integer height,
                             LocalDateTime takenAt, Long uploadedBy) {
    }
}
