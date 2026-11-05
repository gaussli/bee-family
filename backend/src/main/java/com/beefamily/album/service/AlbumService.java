package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Album;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.domain.User;
import com.beefamily.album.dto.album.AlbumDetailResp;
import com.beefamily.album.dto.album.AlbumListResp;
import com.beefamily.album.dto.album.AlbumReq;
import com.beefamily.album.dto.album.AlbumResp;
import com.beefamily.album.dto.album.AlbumUpdateReq;
import com.beefamily.album.dto.album.DeleteAlbumResp;
import com.beefamily.album.dto.album.PhotoStrategy;
import com.beefamily.album.dto.album.UnclassifiedResp;
import com.beefamily.album.mapper.AlbumMapper;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.UserRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.SecurityUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 相册服务。
 *
 * <p><b>未分类虚拟相册</b>（PRD Q6）：不是真实表行，由 {@link #list()} 现算 count + 最新照片封面返回；
 * 它不可删除、不可重命名，因此没有对应写接口。</p>
 *
 * <p><b>封面兜底规则</b>（PRD P0-06③）：用户设了 cover 用 cover；否则取相册内 {@code taken_at} 最新的
 * 照片。规则只写在 {@link #resolveCover}，避免列表 / 详情 / 搜索出现三份实现。</p>
 *
 * <p><b>photoCount 只读</b>：写入由 {@code PhotoService} 在同一事务内维护（±1 / ±N），
 * 这里绝不重算，避免两条写路径互相覆盖。</p>
 */
@Service
public class AlbumService {

    private static final Logger log = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final PhotoService photoService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param albumRepository 相册仓储
     * @param photoRepository 照片仓储
     * @param userRepository  用户仓储（创建者昵称）
     * @param photoService    照片服务（复用删除编排）
     * @param guard           家庭闸门
     */
    public AlbumService(AlbumRepository albumRepository, PhotoRepository photoRepository,
                        UserRepository userRepository, PhotoService photoService, FamilyAccessGuard guard) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.photoService = photoService;
        this.guard = guard;
    }

    /**
     * 相册列表 + 未分类汇总。
     *
     * @return 列表响应
     */
    @Transactional(readOnly = true)
    public AlbumListResp list() {
        Long familyId = guard.familyId();
        List<Album> albums = albumRepository.findByFamilyIdOrderByUpdatedAtDesc(familyId);
        Map<Long, String> nicknames = nicknames(albums.stream().map(Album::getCreatedBy).toList());
        List<AlbumResp> items = new ArrayList<>(albums.size());
        for (Album album : albums) {
            items.add(toRespWithCover(album, familyId, nicknames.get(album.getCreatedBy())));
        }
        long unclassifiedCount = photoRepository.countByFamilyIdAndAlbumIdIsNull(familyId);
        Photo cover = photoRepository.findFirstByFamilyIdAndAlbumIdIsNullOrderByTakenAtDescIdDesc(familyId)
                .orElse(null);
        UnclassifiedResp unclassified = new UnclassifiedResp(unclassifiedCount,
                cover == null ? null : cover.getId(), cover == null ? null : cover.getThumbKey());
        return new AlbumListResp(items, unclassified);
    }

    /**
     * 新建相册。
     *
     * @param req 请求
     * @return 相册响应
     */
    @Transactional
    public AlbumResp create(AlbumReq req) {
        Long familyId = guard.familyId();
        String name = req.name().trim();
        if (albumRepository.existsByFamilyIdAndName(familyId, name)) {
            throw new BusinessException(ErrorCode.ALBUM_NAME_DUPLICATED);
        }
        Album album = new Album();
        album.setFamilyId(familyId);
        album.setName(name);
        album.setDescription(req.description());
        album.setPhotoCount(0);
        album.setCreatedBy(SecurityUtils.currentUserId());
        Album saved = albumRepository.save(album);
        log.info("album created id={} familyId={}", saved.getId(), familyId);
        return toRespWithCover(saved, familyId, nicknameOf(saved.getCreatedBy()));
    }

    /**
     * 相册详情。
     *
     * @param id 相册 id
     * @return 详情响应
     */
    @Transactional(readOnly = true)
    public AlbumDetailResp detail(Long id) {
        Long familyId = guard.familyId();
        Album album = requireAlbum(id, familyId);
        Cover cover = resolveCover(album, familyId);
        LocalDateTime takenFrom = photoRepository.findMinTakenAt(familyId, album.getId());
        LocalDateTime takenTo = photoRepository.findMaxTakenAt(familyId, album.getId());
        boolean ownerEditable = guard.isOwner()
                || Objects.equals(album.getCreatedBy(), SecurityUtils.currentUserId());
        return AlbumMapper.toDetailResp(album, cover.thumbKey(), nicknameOf(album.getCreatedBy()),
                cover.latestTakenAt(), takenFrom, takenTo, ownerEditable);
    }

    /**
     * 更新相册（仅创建者或 owner）。
     *
     * @param id  相册 id
     * @param req 请求
     * @return 相册响应
     */
    @Transactional
    public AlbumResp update(Long id, AlbumUpdateReq req) {
        Long familyId = guard.familyId();
        Album album = requireAlbum(id, familyId);
        guard.assertCreatorOrOwner(album.getCreatedBy());

        if (req.name() != null) {
            String name = req.name().trim();
            if (!name.equals(album.getName())) {
                if (albumRepository.existsByFamilyIdAndName(familyId, name)) {
                    throw new BusinessException(ErrorCode.ALBUM_NAME_DUPLICATED);
                }
                album.setName(name);
            }
        }
        album.setDescription(req.description());
        applyCover(album, req.coverPhotoId(), familyId);
        Album saved = albumRepository.save(album);
        return toRespWithCover(saved, familyId, nicknameOf(saved.getCreatedBy()));
    }

    /**
     * 删除相册并处置照片（仅创建者或 owner）。
     *
     * @param id       相册 id
     * @param strategy 照片处置策略
     * @return 处置结果
     */
    @Transactional
    public DeleteAlbumResp delete(Long id, PhotoStrategy strategy) {
        Long familyId = guard.familyId();
        Album album = requireAlbum(id, familyId);
        guard.assertCreatorOrOwner(album.getCreatedBy());
        PhotoStrategy effective = strategy == null ? PhotoStrategy.move : strategy;

        if (effective == PhotoStrategy.delete) {
            List<Photo> photos = photoRepository.findByFamilyIdAndAlbumId(familyId, album.getId());
            // 复 PhotoService 的删除编排：清标签关联 / 清封面引用 / 维护计数 / afterCommit 删文件
            photoService.deletePhotosBulk(familyId, photos);
            albumRepository.delete(album);
            log.info("album deleted id={} familyId={} strategy=delete photos={}", id, familyId, photos.size());
            return new DeleteAlbumResp(photos.size(), 0);
        }

        // move：显式 UPDATE 才能拿到 movedPhotos 计数（FK 的 SET NULL 只是兜底）
        int moved = photoRepository.moveToUnclassified(familyId, album.getId());
        albumRepository.delete(album);
        log.info("album deleted id={} familyId={} strategy=move moved={}", id, familyId, moved);
        return new DeleteAlbumResp(0, moved);
    }

    /**
     * 转相册响应（供搜索结果复用）。
     *
     * @param album    相册实体
     * @param familyId 家庭 id
     * @return 响应
     */
    @Transactional(readOnly = true)
    public AlbumResp toResp(Album album, Long familyId) {
        return toRespWithCover(album, familyId, nicknameOf(album.getCreatedBy()));
    }

    /**
     * 设置封面：必须是本相册内的照片。
     *
     * @param album        相册
     * @param coverPhotoId 封面照片 id
     * @param familyId     家庭 id
     */
    private void applyCover(Album album, Long coverPhotoId, Long familyId) {
        if (coverPhotoId == null) {
            // null = 清除自定义封面，回到「取相册内最新照片」兜底
            album.setCoverPhotoId(null);
            return;
        }
        Photo photo = photoRepository.findByIdAndFamilyId(coverPhotoId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        if (!Objects.equals(photo.getAlbumId(), album.getId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "封面只能选择本相册内的照片");
        }
        album.setCoverPhotoId(coverPhotoId);
    }

    /**
     * 组装相册响应（含封面解析）。
     *
     * @param album        相册
     * @param familyId     家庭 id
     * @param createdByName 创建者昵称
     * @return 响应
     */
    private AlbumResp toRespWithCover(Album album, Long familyId, String createdByName) {
        Cover cover = resolveCover(album, familyId);
        return AlbumMapper.toResp(album, cover.thumbKey(), createdByName, cover.latestTakenAt());
    }

    /**
     * 解析封面与最新拍摄时间。
     *
     * @param album    相册
     * @param familyId 家庭 id
     * @return 封面信息
     */
    private Cover resolveCover(Album album, Long familyId) {
        Photo latest = photoRepository
                .findFirstByFamilyIdAndAlbumIdOrderByTakenAtDescIdDesc(familyId, album.getId())
                .orElse(null);
        LocalDateTime latestTakenAt = latest == null ? null : latest.getTakenAt();
        if (album.getCoverPhotoId() != null) {
            Photo cover = photoRepository.findByIdAndFamilyId(album.getCoverPhotoId(), familyId).orElse(null);
            if (cover != null) {
                return new Cover(cover.getThumbKey(), latestTakenAt);
            }
            // 封面照片已被删除（悬挂引用）：降级为最新照片，前端不会看到破图
        }
        return new Cover(latest == null ? null : latest.getThumbKey(), latestTakenAt);
    }

    /**
     * 读取家庭内相册，否则 40301。
     *
     * @param id       相册 id
     * @param familyId 家庭 id
     * @return 相册实体
     */
    private Album requireAlbum(Long id, Long familyId) {
        return albumRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
    }

    /**
     * 批量查昵称。
     *
     * @param userIds 用户 id
     * @return id → 昵称
     */
    private Map<Long, String> nicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            result.put(user.getId(), user.getNickname());
        }
        return result;
    }

    /**
     * 单用户昵称。
     *
     * @param userId 用户 id
     * @return 昵称
     */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getNickname).orElse(null);
    }

    /**
     * 封面解析结果。
     *
     * @param thumbKey      封面缩略图 key
     * @param latestTakenAt 相册内最新拍摄时间
     */
    private record Cover(String thumbKey, LocalDateTime latestTakenAt) {
    }
}
