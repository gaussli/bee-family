package com.beefamily.album.service;

import com.beefamily.album.domain.Baby;
import com.beefamily.album.dto.stats.OverviewResp;
import com.beefamily.album.mapper.StatisticsMapper;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.FamilyMemberRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.DateTimes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 概览统计服务。
 *
 * <p><b>实时聚合，禁止缓存</b>（PRD P0-12①）：所有数字在每次请求时现算，
 * 因此不需要「上传后失效缓存」这类容易漏掉的同步逻辑。</p>
 *
 * <p>每个指标都是一次单索引 COUNT，在单家庭量级下均为毫秒级；没有引入快照表。</p>
 */
@Service
public class StatisticsService {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final MilestoneRepository milestoneRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final BabyRepository babyRepository;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param photoRepository        照片仓储
     * @param albumRepository        相册仓储
     * @param milestoneRepository    里程碑仓储
     * @param familyMemberRepository 成员仓储
     * @param babyRepository         宝宝仓储
     * @param guard                  家庭闸门
     */
    public StatisticsService(PhotoRepository photoRepository, AlbumRepository albumRepository,
                             MilestoneRepository milestoneRepository,
                             FamilyMemberRepository familyMemberRepository, BabyRepository babyRepository,
                             FamilyAccessGuard guard) {
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
        this.milestoneRepository = milestoneRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.babyRepository = babyRepository;
        this.guard = guard;
    }

    /**
     * 概览统计。
     *
     * @return 概览
     */
    @Transactional(readOnly = true)
    public OverviewResp overview() {
        Long familyId = guard.familyId();
        long photoCount = photoRepository.countByFamilyId(familyId);
        long albumCount = albumRepository.countByFamilyId(familyId);
        long milestoneCount = milestoneRepository.countByFamilyId(familyId);
        long memberCount = familyMemberRepository.countByFamilyId(familyId);
        long babyCount = babyRepository.countByFamilyId(familyId);
        Integer earliestAgeDays = earliestBabyAgeDays(familyId);
        long monthPhotoCount = photoRepository.countByFamilyIdAndCreatedAtGreaterThanEqual(familyId,
                firstDayOfMonth());
        long storageBytes = photoRepository.sumFileSizeByFamilyId(familyId);
        return StatisticsMapper.toOverview(photoCount, albumCount, milestoneCount, memberCount, babyCount,
                earliestAgeDays, monthPhotoCount, storageBytes);
    }

    /**
     * 最早出生宝宝的日龄（无宝宝返回 null）。
     *
     * @param familyId 家庭 id
     * @return 日龄
     */
    private Integer earliestBabyAgeDays(Long familyId) {
        List<Baby> babies = babyRepository.findByFamilyIdOrderById(familyId);
        if (babies.isEmpty()) {
            return null;
        }
        LocalDate earliest = babies.stream().map(Baby::getBirthday).min(LocalDate::compareTo).orElse(null);
        return earliest == null ? null : DateTimes.ageDays(earliest);
    }

    /**
     * 本月 1 日 00:00:00（Asia/Shanghai）。
     *
     * @return 时间点
     */
    private LocalDateTime firstDayOfMonth() {
        return DateTimes.today().withDayOfMonth(1).atStartOfDay();
    }
}
