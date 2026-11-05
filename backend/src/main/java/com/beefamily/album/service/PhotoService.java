package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.domain.Album;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.photo.BatchMoveReq;
import com.beefamily.album.dto.photo.BatchResultResp;
import com.beefamily.album.dto.photo.PatchField;
import com.beefamily.album.dto.photo.PhotoDetailResp;
import com.beefamily.album.dto.photo.PhotoQuery;
import com.beefamily.album.dto.photo.PhotoResp;
import com.beefamily.album.dto.photo.PhotoUpdateReq;
import com.beefamily.album.dto.photo.PhotoUploadResp;
import com.beefamily.album.dto.photo.UploadFailure;
import com.beefamily.album.mapper.PhotoMapper;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.PhotoTagRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.SecurityUtils;
import com.beefamily.album.security.UserPrincipal;
import com.beefamily.album.support.DateTimes;
import com.beefamily.album.support.ImageType;
import com.beefamily.album.support.ImageValidator;
import com.beefamily.album.support.Limits;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 照片服务：上传编排、浏览、编辑、删除与批量操作。
 *
 * <p><b>上传为什么不在一个事务里</b>：逐张调用 {@link PhotoIngestService#ingest}（独立 REQUIRES_NEW 事务），
 * 一张失败不影响其余；文件先落盘再写 DB，DB 失败则补偿删文件（避免「DB 有记录、磁盘没有文件」的
 * 永久破图，§6.2）。</p>
 *
 * <p><b>删除为什么在事务提交后才删文件</b>：若事务回滚，DB 仍引用这些文件，提前删除无法恢复。</p>
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BATCH_RANDOM_LENGTH = 6;

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagService tagService;
    private final PhotoIngestService photoIngestService;
    private final StorageService storageService;
    private final ThumbnailService thumbnailService;
    private final ExifService exifService;
    private final BabyService babyService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param photoRepository    照片仓储
     * @param albumRepository    相册仓储
     * @param photoTagRepository 照片标签仓储
     * @param tagService         标签服务
     * @param photoIngestService 单张入库服务（独立事务 Bean）
     * @param storageService     存储服务
     * @param thumbnailService   缩略图服务
     * @param exifService        EXIF 服务
     * @param babyService        宝宝服务（批量昵称）
     * @param guard              家庭闸门
     */
    public PhotoService(PhotoRepository photoRepository,
                        AlbumRepository albumRepository,
                        PhotoTagRepository photoTagRepository,
                        TagService tagService,
                        PhotoIngestService photoIngestService,
                        StorageService storageService,
                        ThumbnailService thumbnailService,
                        ExifService exifService,
                        BabyService babyService,
                        FamilyAccessGuard guard) {
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
        this.photoTagRepository = photoTagRepository;
        this.tagService = tagService;
        this.photoIngestService = photoIngestService;
        this.storageService = storageService;
        this.thumbnailService = thumbnailService;
        this.exifService = exifService;
        this.babyService = babyService;
        this.guard = guard;
    }

    /**
     * 照片列表（相册内 / 未分类 / 全家庭，支持三种白名单排序）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<PhotoResp> list(PhotoQuery query) {
        Long familyId = guard.familyId();
        if (!PhotoQuery.isSupportedSort(query.sort())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "参数 sort 取值非法，允许 takenAt_desc/takenAt_asc/createdAt_desc");
        }
        PageQuery pageQuery = query.pageQuery();
        PageRequest pageRequest = PageRequest.of(pageQuery.page(), pageQuery.size(), resolveSort(query.sort()));
        Page<Photo> page;
        if (query.albumId() != null) {
            requireAlbum(query.albumId(), familyId);
            page = photoRepository.findByFamilyIdAndAlbumId(familyId, query.albumId(), pageRequest);
        } else if (query.unclassified()) {
            page = photoRepository.findByFamilyIdAndAlbumIdIsNull(familyId, pageRequest);
        } else {
            page = photoRepository.findByFamilyId(familyId, pageRequest);
        }
        List<PhotoResp> items = toRespBatch(page.getContent(), familyId);
        return PageResponse.of(items, pageQuery, page.getTotalElements());
    }

    /**
     * 照片详情。
     *
     * @param id 照片 id
     * @return 详情
     */
    @Transactional(readOnly = true)
    public PhotoDetailResp detail(Long id) {
        Long familyId = guard.familyId();
        Photo photo = requirePhoto(id, familyId);
        String albumName = albumNameOf(photo.getAlbumId(), familyId);
        List<String> tags = tagService.tagNamesByPhotoIds(List.of(photo.getId()))
                .getOrDefault(photo.getId(), List.of());
        String uploaderName = babyService.nicknameOf(photo.getUploadedBy());
        return PhotoMapper.toDetailResp(photo, albumName, tags, uploaderName);
    }

    /**
     * 批量上传。
     *
     * @param files   文件（1-100）
     * @param albumId 目标相册 id，可空
     * @return 批量结果（部分失败仍返回成功响应）
     */
    public PhotoUploadResp upload(List<MultipartFile> files, Long albumId) {
        Long familyId = guard.familyId();
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择要上传的照片");
        }
        if (files.size() > Limits.MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.BATCH_TOO_LARGE);
        }
        String albumName = null;
        if (albumId != null) {
            albumName = requireAlbum(albumId, familyId).getName();
        }

        String batchId = buildBatchId();
        List<PhotoResp> successes = new ArrayList<>();
        List<UploadFailure> failures = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            // 先做体积判断：超限文件不必读入内存
            if (file.getSize() > Limits.MAX_FILE_SIZE_BYTES) {
                failures.add(new UploadFailure(fileName, ErrorCode.FILE_TOO_LARGE.getCode(),
                        ErrorCode.FILE_TOO_LARGE.getDefaultMessage()));
                continue;
            }
            if (file.getSize() <= 0) {
                failures.add(new UploadFailure(fileName, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
                        "文件为空。" + Limits.UNSUPPORTED_HINT));
                continue;
            }
            List<String> writtenKeys = new ArrayList<>();
            try {
                Photo photo = ingestOne(file, familyId, albumId, albumName, principal, writtenKeys);
                successes.add(PhotoMapper.toResp(photo, albumName, List.of(), principal.nickname()));
            } catch (BusinessException ex) {
                cleanup(writtenKeys);
                failures.add(new UploadFailure(fileName, ex.getErrorCode().getCode(), ex.getMessage()));
            } catch (Exception ex) {
                cleanup(writtenKeys);
                log.error("upload failed batchId={} file={}", batchId, fileName, ex);
                failures.add(new UploadFailure(fileName, ErrorCode.STORAGE_WRITE_FAILED.getCode(),
                        ErrorCode.STORAGE_WRITE_FAILED.getDefaultMessage()));
            }
        }
        log.info("upload batchId={} familyId={} success={} failure={}", batchId, familyId, successes.size(),
                failures.size());
        return new PhotoUploadResp(batchId, successes.size(), failures.size(), successes, failures);
    }

    /**
     * 更新照片（PATCH）。
     *
     * <p><b>{@code albumId} 是三态字段</b>（{@link PatchField}）：字段缺席 = 这次编辑与相册无关，
     * 显式 {@code null} = 移到未分类，数字 = 移到该相册。之前把「缺席」也当成 {@code null} 处理，
     * 导致「只改标题」会把照片静默踢出相册、相册计数 -1（QA P1-①）。</p>
     *
     * @param id  照片 id
     * @param req 请求
     * @return 更新后的照片
     */
    @Transactional
    public PhotoResp update(Long id, PhotoUpdateReq req) {
        Long familyId = guard.familyId();
        Photo photo = requirePhoto(id, familyId);
        guard.assertUploaderOrCreatorOrOwner(photo.getUploadedBy(), photo.getUploadedBy());

        photo.setTitle(req.title() == null ? null : req.title().trim());
        photo.setDescription(req.description());
        photo.setLocationName(req.locationName());
        if (req.takenAt() != null) {
            // taken_at 是 NOT NULL 列：null 视为「不修改」，避免把拍摄时间清成非法值
            photo.setTakenAt(req.takenAt());
        }

        PatchField<Long> albumPatch = req.albumId();
        if (albumPatch != null && albumPatch.isPresent()) {
            Long targetAlbumId = toAlbumId(albumPatch.value());
            if (!Objects.equals(photo.getAlbumId(), targetAlbumId)) {
                if (targetAlbumId != null) {
                    // 跨家庭/不存在的相册统一 40907（「目标相册不存在或无权访问」）
                    requireAlbumForMove(targetAlbumId, familyId);
                }
                Map<Long, Integer> deltas = new LinkedHashMap<>();
                deltas.merge(photo.getAlbumId() == null ? -1L : photo.getAlbumId(), -1, Integer::sum);
                deltas.merge(targetAlbumId == null ? -1L : targetAlbumId, 1, Integer::sum);
                photo.setAlbumId(targetAlbumId);
                // 先改计数再写 photo：photo 的 UPDATE 会给目标相册加外键共享锁，
                // 反过来做就是 S→X 锁升级（与上传路径同一个根因）
                applyAlbumCountDeltas(familyId, deltas);
            }
        }

        Photo saved = photoRepository.save(photo);
        List<String> tags = req.tags() == null
                ? tagService.tagNamesByPhotoIds(List.of(saved.getId())).getOrDefault(saved.getId(), List.of())
                : tagService.applyToPhoto(familyId, saved.getId(), req.tags());
        return PhotoMapper.toResp(saved, albumNameOf(saved.getAlbumId(), familyId), tags,
                babyService.nicknameOf(saved.getUploadedBy()));
    }

    /**
     * 把三态字段里的值转成相册 id。
     *
     * <p>反序列化时泛型信息理论上能拿到 {@code Long}，但 record 组件偶发退化为 {@code Object}，
     * 这里统一按 {@link Number} 兜底，避免 {@code ClassCastException} 冒成 500。</p>
     *
     * @param value 原始值
     * @return 相册 id，null 表示移到未分类
     */
    private Long toAlbumId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "albumId 必须是数字");
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAM, "albumId 必须是数字");
    }

    /**
     * 删除单张照片。
     *
     * @param id 照片 id
     */
    @Transactional
    public void delete(Long id) {
        Long familyId = guard.familyId();
        Photo photo = requirePhoto(id, familyId);
        guard.assertUploaderOrCreatorOrOwner(photo.getUploadedBy(), photo.getUploadedBy());
        deletePhotosBulk(familyId, List.of(photo));
    }

    /**
     * 批量删除。
     *
     * @param photoIds 照片 id 列表
     * @return 删除条数
     */
    @Transactional
    public BatchResultResp batchDelete(List<Long> photoIds) {
        Long familyId = guard.familyId();
        List<Photo> photos = loadForBatch(photoIds, familyId);
        for (Photo photo : photos) {
            guard.assertUploaderOrCreatorOrOwner(photo.getUploadedBy(), photo.getUploadedBy());
        }
        deletePhotosBulk(familyId, photos);
        return new BatchResultResp(photos.size(), null);
    }

    /**
     * 批量移动（含移到未分类）。
     *
     * @param req 请求
     * @return 移动条数
     */
    @Transactional
    public BatchResultResp batchMove(BatchMoveReq req) {
        Long familyId = guard.familyId();
        List<Photo> photos = loadForBatch(req.photoIds(), familyId);
        if (req.albumId() != null) {
            requireAlbumForMove(req.albumId(), familyId);
        }
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        int moved = 0;
        for (Photo photo : photos) {
            if (Objects.equals(photo.getAlbumId(), req.albumId())) {
                continue;
            }
            deltas.merge(photo.getAlbumId() == null ? -1L : photo.getAlbumId(), -1, Integer::sum);
            deltas.merge(req.albumId() == null ? -1L : req.albumId(), 1, Integer::sum);
            photo.setAlbumId(req.albumId());
            moved++;
        }
        // 先改计数再写 photo：photo 的 UPDATE 会为新相册加外键共享锁，
        // 若此时再去升级 album 行的排他锁就会死锁（与上传路径同一个根因）
        applyAlbumCountDeltas(familyId, deltas);
        photoRepository.saveAll(photos);
        log.info("batch move familyId={} moved={} targetAlbumId={}", familyId, moved, req.albumId());
        return new BatchResultResp(null, moved);
    }

    /**
     * 删除照片及其全部关联（供本服务与 {@link AlbumService} 复用）。
     *
     * <p>执行顺序：photo_tag → 相册计数（主键行锁）→ 相册封面 → 家庭封面 → photo 行
     * → afterCommit 删 3 个文件。{@code album.cover_photo_id} 与 {@code family.cover_photo_id}
     * 没有外键，必须显式清理，否则会留下指向已删照片的悬挂引用（§4.3）。</p>
     *
     * <p><b>顺序不是随便排的</b>：所有相册写操作都按「主键 → 二级索引」的固定顺序加锁，
     * 与 {@code AlbumService} 的设封面路径同序，否则 InnoDB 会在
     * {@code PRIMARY} 行锁与 {@code idx_album_cover_photo} 间隙锁之间形成环。</p>
     *
     * @param familyId 家庭 id
     * @param photos   照片实体列表
     */
    @Transactional
    public void deletePhotosBulk(Long familyId, List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        List<Long> ids = photos.stream().map(Photo::getId).toList();
        photoTagRepository.deleteByPhotoIdIn(ids);

        Map<Long, Integer> deltas = new LinkedHashMap<>();
        for (Photo photo : photos) {
            if (photo.getAlbumId() != null) {
                deltas.merge(photo.getAlbumId(), -1, Integer::sum);
            }
        }
        // 加锁顺序必须与「设封面」路径一致：先按主键拿相册行锁，再改二级索引。
        // 反过来的话，范围更新锁住了 idx_album_cover_photo 的间隙，另一侧是先主键后索引，
        // 两边互等 → 实测「设封面 × 删同一张照片」20 轮 20 次死锁（P1-③）。
        applyAlbumCountDeltas(familyId, deltas);
        clearAlbumCovers(familyId, ids);
        photoRepository.clearFamilyCoverByPhotoIds(ids);

        photoRepository.deleteAll(photos);

        List<String> keys = new ArrayList<>();
        for (Photo photo : photos) {
            addKey(keys, photo.getStorageKey());
            addKey(keys, photo.getThumbKey());
            addKey(keys, photo.getPreviewKey());
        }
        runAfterCommit(() -> storageService.deleteQuietly(keys));
    }

    /**
     * 批量转照片响应（时间轴 / 搜索复用）。
     *
     * @param photos   照片列表
     * @param familyId 家庭 id
     * @return 响应列表
     */
    @Transactional(readOnly = true)
    public List<PhotoResp> toRespBatch(Collection<Photo> photos, Long familyId) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        List<Long> albumIds = photos.stream().map(Photo::getAlbumId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> albumNames = new HashMap<>();
        if (!albumIds.isEmpty()) {
            for (Album album : albumRepository.findByIdInAndFamilyId(albumIds, familyId)) {
                albumNames.put(album.getId(), album.getName());
            }
        }
        Map<Long, String> nicknames = babyService.nicknames(photos.stream().map(Photo::getUploadedBy).toList());
        Map<Long, List<String>> tags = tagService.tagNamesByPhotoIds(
                photos.stream().map(Photo::getId).toList());
        List<PhotoResp> result = new ArrayList<>(photos.size());
        for (Photo photo : photos) {
            result.add(PhotoMapper.toResp(photo,
                    photo.getAlbumId() == null ? null : albumNames.get(photo.getAlbumId()),
                    tags.getOrDefault(photo.getId(), List.of()),
                    nicknames.get(photo.getUploadedBy())));
        }
        return result;
    }

    /**
     * 处理单个上传文件：嗅探 → 落盘 → EXIF → 缩略图 → 入库（独立事务）。
     *
     * @param file        上传文件
     * @param familyId    家庭 id
     * @param albumId     相册 id
     * @param albumName   相册名（仅日志）
     * @param principal   当前用户
     * @param writtenKeys 已写入的 key（失败时用于补偿删除）
     * @return 已入库的照片
     */
    private Photo ingestOne(MultipartFile file, Long familyId, Long albumId, String albumName,
                            UserPrincipal principal, List<String> writtenKeys) throws java.io.IOException {
        byte[] bytes = file.getBytes();
        // 魔数优先：Content-Type 与后缀都可伪造，只有文件头可信
        ImageType type = ImageValidator.validate(bytes, file.getOriginalFilename());
        LocalDate today = DateTimes.today();
        String storageKey = storageService.allocateKey(familyId, today, type.getExtension());
        storageService.writeBytes(storageKey, bytes);
        writtenKeys.add(storageKey);

        String keyBase = storageService.keyBase(storageKey);
        ThumbnailService.ThumbnailResult thumbnail = thumbnailService.generate(storageKey, keyBase, type);
        String thumbKey;
        String previewKey;
        Integer width = null;
        Integer height = null;
        if (thumbnail.generated()) {
            thumbKey = thumbnail.thumbKey();
            previewKey = thumbnail.previewKey();
            width = thumbnail.width();
            height = thumbnail.height();
            writtenKeys.add(thumbKey);
            writtenKeys.add(previewKey);
        } else {
            // 运行环境无法解码/编码该格式（例如缺少 WebP ImageIO 插件）：
            // 回退为直接使用原图 key，保证「上传成功即可见」，代价是网格加载原图（已知限制，见交付说明）
            thumbKey = storageKey;
            previewKey = storageKey;
        }

        LocalDateTime takenAt = exifService.readTakenAt(bytes).orElse(DateTimes.now());
        PhotoIngestService.PhotoDraft draft = new PhotoIngestService.PhotoDraft(familyId, albumId, storageKey,
                thumbKey, previewKey, type.getMimeType(), file.getSize(), width, height, takenAt,
                principal.userId());
        Photo photo = ingestWithRetry(draft);
        log.debug("photo uploaded album={} key={}", albumName, storageKey);
        return photo;
    }

    /**
     * 入库并对「锁冲突」重试一次。
     *
     * <p>原子计数已经消除了实测到的死锁路径，但 InnoDB 仍可能因间隙锁/唯一索引在极端并发下
     * 回滚一次事务。这类异常（{@code DeadlockLoserDataAccessException} /
     * {@code CannotAcquireLockException}，共同父类是 {@link PessimisticLockingFailureException}）
     * 是**可重试**的，而原来它被 {@code catch (Exception)} 吞成了 50002，用户只能手动点重试。</p>
     *
     * <p>重试粒度是「这一张照片的入库事务」，不是整批：文件已经落盘，重试只重跑 DB 部分，
     * 不会重复写文件。</p>
     *
     * @param draft 照片草稿
     * @return 已入库的照片
     */
    private Photo ingestWithRetry(PhotoIngestService.PhotoDraft draft) {
        try {
            return photoIngestService.ingest(draft);
        } catch (PessimisticLockingFailureException ex) {
            log.warn("ingest lock conflict, retrying once albumId={} storageKey={} cause={}", draft.albumId(),
                    draft.storageKey(), ex.getClass().getSimpleName());
            return photoIngestService.ingest(draft);
        }
    }

    /**
     * 解析排序（白名单，禁止把用户输入直接拼进 ORDER BY）。
     *
     * @param sort 排序参数
     * @return 排序对象
     */
    private Sort resolveSort(String sort) {
        return switch (sort) {
            case PhotoQuery.SORT_TAKEN_ASC -> Sort.by(Sort.Order.asc("takenAt"), Sort.Order.asc("id"));
            case PhotoQuery.SORT_CREATED_DESC -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("takenAt"), Sort.Order.desc("id"));
        };
    }

    /**
     * 读取家庭内照片，否则 40301。
     *
     * @param id       照片 id
     * @param familyId 家庭 id
     * @return 照片实体
     */
    private Photo requirePhoto(Long id, Long familyId) {
        return photoRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
    }

    /**
     * 读取家庭内相册，否则 40301。
     *
     * @param albumId  相册 id
     * @param familyId 家庭 id
     * @return 相册实体
     */
    private Album requireAlbum(Long albumId, Long familyId) {
        return albumRepository.findByIdAndFamilyId(albumId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
    }

    /**
     * 读取移动目标的相册，否则 40907。
     *
     * @param albumId  相册 id
     * @param familyId 家庭 id
     * @return 相册实体
     */
    private Album requireAlbumForMove(Long albumId, Long familyId) {
        return albumRepository.findByIdAndFamilyId(albumId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_ALBUM_MOVE));
    }

    /**
     * 批量读取并校验照片归属（缺任何一张即 40301）。
     *
     * @param photoIds 照片 id
     * @param familyId 家庭 id
     * @return 照片实体
     */
    private List<Photo> loadForBatch(List<Long> photoIds, Long familyId) {
        if (photoIds == null || photoIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请至少选择一张照片");
        }
        List<Long> unique = photoIds.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请至少选择一张照片");
        }
        if (unique.size() > Limits.MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.BATCH_TOO_LARGE);
        }
        List<Photo> photos = photoRepository.findByIdInAndFamilyId(unique, familyId);
        if (photos.size() != unique.size()) {
            throw new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS);
        }
        return photos;
    }

    /**
     * 清空指向被删照片的相册封面。
     *
     * <p>先按 {@code family_id + cover_photo_id} 查出受影响的相册，再逐条按主键条件置空，
     * 而不是一条 {@code update album set cover_photo_id = null where cover_photo_id in (...)}：
     * 后者是二级索引范围更新，会锁住 {@code idx_album_cover_photo} 的范围间隙（含 supremum），
     * 与「设封面」的加锁顺序形成环（QA P1-③，实测 20 轮 20 次死锁）。</p>
     *
     * @param familyId 家庭 id
     * @param photoIds 被删照片 id
     */
    private void clearAlbumCovers(Long familyId, List<Long> photoIds) {
        List<Album> covered = albumRepository.findByFamilyIdAndCoverPhotoIdIn(familyId, photoIds);
        for (Album album : covered) {
            albumRepository.clearCover(album.getId(), photoIds);
        }
    }

    /**
     * 应用相册计数增量（负数即减），**单条原子 UPDATE**。
     *
     * <p>原来这里是 {@code findById + setPhotoCount + save}：一次整行 UPDATE，既可能丢更新，
     * 又会和并发上传的 INSERT（外键共享锁）互等成死锁。改为 {@link AlbumRepository#addPhotoCount}
     * 的原子自增/自减后，同一相册的并发增删只会按行锁串行，不会再死锁。</p>
     *
     * @param familyId 家庭 id
     * @param deltas   相册 id → 增量（-1L 键表示「未分类」，无计数需要维护）
     */
    private void applyAlbumCountDeltas(Long familyId, Map<Long, Integer> deltas) {
        LocalDateTime now = DateTimes.now();
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            Long albumId = entry.getKey();
            if (albumId == null || albumId < 0 || entry.getValue() == 0) {
                continue;
            }
            int affected = albumRepository.addPhotoCount(albumId, familyId, entry.getValue(), now);
            if (affected == 0) {
                // 减法被 photo_count >= 0 守卫挡住（计数本来就已经是 0）或相册已不存在：
                // 属于可自愈的计数漂移，记 WARN 但不打断业务
                log.warn("album photo_count unchanged albumId={} delta={} familyId={}", albumId,
                        entry.getValue(), familyId);
            }
        }
    }

    /**
     * 相册名。
     *
     * @param albumId  相册 id
     * @param familyId 家庭 id
     * @return 相册名，albumId 为空返回 null
     */
    private String albumNameOf(Long albumId, Long familyId) {
        if (albumId == null) {
            return null;
        }
        return albumRepository.findByIdAndFamilyId(albumId, familyId).map(Album::getName).orElse(null);
    }

    /**
     * 清理已写入的文件（补偿）。
     *
     * @param keys key 列表
     */
    private void cleanup(List<String> keys) {
        storageService.deleteQuietly(keys);
    }

    /**
     * 添加非空 key。
     *
     * @param keys 目标列表
     * @param key  待添加 key
     */
    private void addKey(List<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }

    /**
     * 生成批次号 {@code u-yyyyMMdd-6hex}（仅用于日志关联）。
     *
     * @return 批次号
     */
    private String buildBatchId() {
        byte[] bytes = new byte[BATCH_RANDOM_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder suffix = new StringBuilder();
        for (byte b : bytes) {
            suffix.append(HEX[(b & 0xFF) >>> 4]).append(HEX[b & 0x0F]);
        }
        return "u-" + DateTimes.today().format(BATCH_DATE) + "-" + suffix;
    }

    /**
     * 注册事务提交后动作（无事务时立即执行）。
     *
     * @param action 动作
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
