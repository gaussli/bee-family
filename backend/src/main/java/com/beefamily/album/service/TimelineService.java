package com.beefamily.album.service;

import com.beefamily.album.common.PageQuery;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.domain.Milestone;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.dto.photo.PhotoResp;
import com.beefamily.album.dto.timeline.TimelineGroupResp;
import com.beefamily.album.dto.timeline.TimelineItemResp;
import com.beefamily.album.dto.timeline.TimelinePageResp;
import com.beefamily.album.mapper.GrowthMapper;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.TimelineQueryRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.DateTimes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 时间轴聚合服务。
 *
 * <p><b>三步走</b>：① 取排序键（SQL UNION ALL，全局序）→ ② 按类型分组批量回查明细（3 条 IN + 1 条标签 IN）
 * → ③ **严格按 keys 顺序**重建 items 并按 {@code yyyy-MM} 分组。</p>
 *
 * <p>为什么第 ③ 步不能依赖查询返回顺序或 HashMap 迭代顺序：明细查询的返回顺序与 SQL 全局序无关，
 * 一旦顺序漂移，翻页就会出现「重复 / 漏项」，这是时间轴最致命的正确性问题（§6.3）。</p>
 */
@Service
public class TimelineService {

    private final TimelineQueryRepository timelineQueryRepository;
    private final PhotoRepository photoRepository;
    private final MilestoneRepository milestoneRepository;
    private final GrowthRecordRepository growthRecordRepository;
    private final BabyRepository babyRepository;
    private final PhotoService photoService;
    private final MilestoneService milestoneService;
    private final BabyService babyService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param timelineQueryRepository 排序键仓储（唯一原生 SQL）
     * @param photoRepository         照片仓储
     * @param milestoneRepository     里程碑仓储
     * @param growthRecordRepository  成长记录仓储
     * @param babyRepository          宝宝仓储
     * @param photoService            照片服务（批量转响应）
     * @param milestoneService        里程碑服务（批量转响应）
     * @param babyService             宝宝服务（昵称）
     * @param guard                   家庭闸门
     */
    public TimelineService(TimelineQueryRepository timelineQueryRepository, PhotoRepository photoRepository,
                           MilestoneRepository milestoneRepository, GrowthRecordRepository growthRecordRepository,
                           BabyRepository babyRepository, PhotoService photoService,
                           MilestoneService milestoneService, BabyService babyService, FamilyAccessGuard guard) {
        this.timelineQueryRepository = timelineQueryRepository;
        this.photoRepository = photoRepository;
        this.milestoneRepository = milestoneRepository;
        this.growthRecordRepository = growthRecordRepository;
        this.babyRepository = babyRepository;
        this.photoService = photoService;
        this.milestoneService = milestoneService;
        this.babyService = babyService;
        this.guard = guard;
    }

    /**
     * 时间轴分页。
     *
     * @param query 分页参数
     * @return 时间轴页
     */
    @Transactional(readOnly = true)
    public TimelinePageResp page(PageQuery query) {
        Long familyId = guard.familyId();
        PageQuery normalized = query.normalize(PageQuery.DEFAULT_TIMELINE_SIZE);
        long total = timelineQueryRepository.countAll(familyId);
        int totalPages = (int) ((total + normalized.size() - 1) / normalized.size());

        // 多取 1 条用于 hasMore 探测：不信 total（count 与数据可能瞬时不一致）
        List<TimelineQueryRepository.TimelineKey> keys = timelineQueryRepository.fetchKeys(familyId,
                normalized.offset(), normalized.size() + 1);
        boolean hasMore = keys.size() > normalized.size();
        List<TimelineQueryRepository.TimelineKey> pageKeys = hasMore
                ? keys.subList(0, normalized.size())
                : keys;
        if (pageKeys.isEmpty()) {
            return new TimelinePageResp(List.of(), normalized.page(), normalized.size(), total, totalPages, false);
        }

        Map<Long, PhotoResp> photos = loadPhotos(pageKeys, familyId);
        Map<Long, MilestoneResp> milestones = loadMilestones(pageKeys, familyId);
        Map<Long, GrowthRecordResp> growths = loadGrowthRecords(pageKeys, familyId);

        List<TimelineGroupResp> groups = new ArrayList<>();
        List<TimelineItemResp> currentItems = null;
        String currentMonth = null;
        for (TimelineQueryRepository.TimelineKey key : pageKeys) {
            String month = DateTimes.monthKey(key.sortTime());
            if (!month.equals(currentMonth)) {
                currentItems = new ArrayList<>();
                groups.add(new TimelineGroupResp(month, DateTimes.monthLabel(month), currentItems));
                currentMonth = month;
            }
            currentItems.add(buildItem(key, photos, milestones, growths));
        }
        return new TimelinePageResp(groups, normalized.page(), normalized.size(), total, totalPages, hasMore);
    }

    /**
     * 按 key 顺序构建条目。
     *
     * @param key        排序键
     * @param photos     照片明细
     * @param milestones 里程碑明细
     * @param growths    成长记录明细
     * @return 条目
     */
    private TimelineItemResp buildItem(TimelineQueryRepository.TimelineKey key, Map<Long, PhotoResp> photos,
                                       Map<Long, MilestoneResp> milestones,
                                       Map<Long, GrowthRecordResp> growths) {
        return switch (key.itemType()) {
            case TimelineQueryRepository.TYPE_PHOTO -> new TimelineItemResp(key.itemType(), key.itemId(),
                    key.sortTime(), photos.get(key.itemId()), null, null);
            case TimelineQueryRepository.TYPE_MILESTONE -> new TimelineItemResp(key.itemType(), key.itemId(),
                    key.sortTime(), null, milestones.get(key.itemId()), null);
            default -> new TimelineItemResp(TimelineQueryRepository.TYPE_GROWTH, key.itemId(), key.sortTime(),
                    null, null, growths.get(key.itemId()));
        };
    }

    /**
     * 批量取照片明细。
     *
     * @param keys     排序键
     * @param familyId 家庭 id
     * @return id → 响应
     */
    private Map<Long, PhotoResp> loadPhotos(List<TimelineQueryRepository.TimelineKey> keys, Long familyId) {
        List<Long> ids = idsOf(keys, TimelineQueryRepository.TYPE_PHOTO);
        Map<Long, PhotoResp> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Photo> photos = photoRepository.findAllByIdIn(ids);
        for (PhotoResp resp : photoService.toRespBatch(photos, familyId)) {
            result.put(resp.id(), resp);
        }
        return result;
    }

    /**
     * 批量取里程碑明细。
     *
     * @param keys     排序键
     * @param familyId 家庭 id
     * @return id → 响应
     */
    private Map<Long, MilestoneResp> loadMilestones(List<TimelineQueryRepository.TimelineKey> keys, Long familyId) {
        List<Long> ids = idsOf(keys, TimelineQueryRepository.TYPE_MILESTONE);
        Map<Long, MilestoneResp> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Milestone> milestones = milestoneRepository.findAllByIdIn(ids);
        for (MilestoneResp resp : milestoneService.toRespBatch(milestones, familyId)) {
            result.put(resp.id(), resp);
        }
        return result;
    }

    /**
     * 批量取成长记录明细（宝宝昵称一次批查）。
     *
     * @param keys     排序键
     * @param familyId 家庭 id
     * @return id → 响应
     */
    private Map<Long, GrowthRecordResp> loadGrowthRecords(List<TimelineQueryRepository.TimelineKey> keys,
                                                          Long familyId) {
        List<Long> ids = idsOf(keys, TimelineQueryRepository.TYPE_GROWTH);
        Map<Long, GrowthRecordResp> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<GrowthRecord> records = growthRecordRepository.findAllByIdIn(ids);
        List<Long> babyIds = records.stream().map(GrowthRecord::getBabyId).distinct().toList();
        Map<Long, Baby> babies = new HashMap<>();
        for (Baby baby : babyRepository.findByIdInAndFamilyId(babyIds, familyId)) {
            babies.put(baby.getId(), baby);
        }
        Map<Long, String> nicknames = babyService.nicknames(records.stream()
                .map(GrowthRecord::getCreatedBy).toList());
        for (GrowthRecord record : records) {
            Baby baby = babies.get(record.getBabyId());
            result.put(record.getId(), GrowthMapper.toResp(record, baby == null ? null : baby.getName(),
                    nicknames.get(record.getCreatedBy())));
        }
        return result;
    }

    /**
     * 提取某类型的 id 列表。
     *
     * @param keys     排序键
     * @param itemType 类型
     * @return id 列表
     */
    private List<Long> idsOf(List<TimelineQueryRepository.TimelineKey> keys, String itemType) {
        return keys.stream().filter(key -> itemType.equals(key.itemType()))
                .map(TimelineQueryRepository.TimelineKey::itemId).toList();
    }
}
