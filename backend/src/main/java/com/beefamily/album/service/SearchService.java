package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Album;
import com.beefamily.album.domain.Milestone;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.album.AlbumResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.dto.photo.PhotoResp;
import com.beefamily.album.dto.search.SearchGroupResp;
import com.beefamily.album.dto.search.SearchResp;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.Limits;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局搜索服务。
 *
 * <p><b>已知瓶颈</b>（§12 R-2）：{@code LIKE '%q%'} 无法走索引，单家庭 5 万张照片时 P95 约 20–60ms。
 * 缓解手段：强制 {@code family_id} 前缀过滤把扫描面限制在单家庭、关键词长度 ≤50、每组仅回 20 条且只
 * {@code LIMIT}。</p>
 *
 * <p><b>高亮交给前端</b>：后端原样返回命中内容，避免「后端拼 HTML」带来的 XSS 面
 * （前端必须转义后再包 {@code <mark>}）。</p>
 */
@Service
public class SearchService {

    /** 允许的搜索类型。 */
    private static final Set<String> SUPPORTED_TYPES = Set.of("all", "photo", "album", "milestone");

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final MilestoneRepository milestoneRepository;
    private final PhotoService photoService;
    private final AlbumService albumService;
    private final MilestoneService milestoneService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param photoRepository     照片仓储
     * @param albumRepository     相册仓储
     * @param milestoneRepository 里程碑仓储
     * @param photoService        照片服务
     * @param albumService        相册服务
     * @param milestoneService    里程碑服务
     * @param guard               家庭闸门
     */
    public SearchService(PhotoRepository photoRepository, AlbumRepository albumRepository,
                         MilestoneRepository milestoneRepository, PhotoService photoService,
                         AlbumService albumService, MilestoneService milestoneService, FamilyAccessGuard guard) {
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
        this.milestoneRepository = milestoneRepository;
        this.photoService = photoService;
        this.albumService = albumService;
        this.milestoneService = milestoneService;
        this.guard = guard;
    }

    /**
     * 搜索。
     *
     * @param keyword 关键词（1-50 字符）
     * @param type    类型：all / photo / album / milestone
     * @param limit   每组条数（1-50，默认 20）
     * @return 搜索结果
     */
    @Transactional(readOnly = true)
    public SearchResp search(String keyword, String type, int limit) {
        Long familyId = guard.familyId();
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "搜索关键词不能为空");
        }
        if (query.length() > Limits.SEARCH_KEYWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "搜索关键词不能超过 " + Limits.SEARCH_KEYWORD_MAX_LENGTH + " 个字符");
        }
        String typeValue = (type == null || type.isBlank()) ? "all" : type.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(typeValue)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "参数 type 取值非法，允许 all/photo/album/milestone");
        }
        int pageSize = limit <= 0 ? Limits.SEARCH_GROUP_LIMIT : Math.min(limit, Limits.SEARCH_GROUP_LIMIT);

        String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
        SearchGroupResp<PhotoResp> photos = searchPhotos(familyId, pattern, typeValue, pageSize);
        SearchGroupResp<AlbumResp> albums = searchAlbums(familyId, pattern, typeValue, pageSize);
        SearchGroupResp<MilestoneResp> milestones = searchMilestones(familyId, pattern, typeValue, pageSize);

        long totalCount = photos.total() + albums.total() + milestones.total();
        return new SearchResp(query, totalCount, photos, albums, milestones);
    }

    /**
     * 搜索照片（标题 / 描述 / 标签）。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param type     类型过滤
     * @param pageSize 每组条数
     * @return 分组结果
     */
    private SearchGroupResp<PhotoResp> searchPhotos(Long familyId, String pattern, String type, int pageSize) {
        if (!"all".equals(type) && !"photo".equals(type)) {
            return SearchGroupResp.of(0, List.of());
        }
        Page<Photo> page = photoRepository.search(familyId, pattern, PageRequest.of(0, pageSize));
        List<PhotoResp> items = photoService.toRespBatch(page.getContent(), familyId);
        return SearchGroupResp.of(page.getTotalElements(), items);
    }

    /**
     * 搜索相册（名称）。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param type     类型过滤
     * @param pageSize 每组条数
     * @return 分组结果
     */
    private SearchGroupResp<AlbumResp> searchAlbums(Long familyId, String pattern, String type, int pageSize) {
        if (!"all".equals(type) && !"album".equals(type)) {
            return SearchGroupResp.of(0, List.of());
        }
        Page<Album> page = albumRepository.search(familyId, pattern, PageRequest.of(0, pageSize));
        List<AlbumResp> items = page.getContent().stream()
                .map(album -> albumService.toResp(album, familyId))
                .toList();
        return SearchGroupResp.of(page.getTotalElements(), items);
    }

    /**
     * 搜索里程碑（标题 / 描述）。
     *
     * @param familyId 家庭 id
     * @param pattern  已小写化的 {@code %keyword%}
     * @param type     类型过滤
     * @param pageSize 每组条数
     * @return 分组结果
     */
    private SearchGroupResp<MilestoneResp> searchMilestones(Long familyId, String pattern, String type,
                                                            int pageSize) {
        if (!"all".equals(type) && !"milestone".equals(type)) {
            return SearchGroupResp.of(0, List.of());
        }
        Page<Milestone> page = milestoneRepository.search(familyId, pattern, PageRequest.of(0, pageSize));
        List<MilestoneResp> items = milestoneService.toRespBatch(page.getContent(), familyId);
        return SearchGroupResp.of(page.getTotalElements(), items);
    }
}
