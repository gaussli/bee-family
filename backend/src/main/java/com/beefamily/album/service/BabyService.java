package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.domain.Milestone;
import com.beefamily.album.domain.User;
import com.beefamily.album.dto.baby.BabyDetailResp;
import com.beefamily.album.dto.baby.BabyReq;
import com.beefamily.album.dto.baby.BabyResp;
import com.beefamily.album.dto.baby.BabyUpdateReq;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.mapper.BabyMapper;
import com.beefamily.album.mapper.GrowthMapper;
import com.beefamily.album.mapper.MilestoneMapper;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.UserRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.Limits;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宝宝档案服务。
 *
 * <p>数量上限 3 个在 service 校验（DB 无唯一约束，因为兄弟可重名）；删除宝宝依赖 DDL 的
 * {@code growth_record ON DELETE CASCADE} 与 {@code milestone.baby_id ON DELETE SET NULL}
 * 完成级联，service 不重复写删除逻辑（DDL 是唯一定义处）。</p>
 */
@Service
public class BabyService {

    private static final Logger log = LoggerFactory.getLogger(BabyService.class);

    private final BabyRepository babyRepository;
    private final MilestoneRepository milestoneRepository;
    private final GrowthRecordRepository growthRecordRepository;
    private final UserRepository userRepository;
    private final com.beefamily.album.repository.PhotoRepository photoRepository;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param babyRepository         宝宝仓储
     * @param milestoneRepository    里程碑仓储
     * @param growthRecordRepository 成长记录仓储
     * @param userRepository         用户仓储
     * @param photoRepository        照片仓储（取里程碑配图缩略图）
     * @param guard                  家庭闸门
     */
    public BabyService(BabyRepository babyRepository,
                       MilestoneRepository milestoneRepository,
                       GrowthRecordRepository growthRecordRepository,
                       UserRepository userRepository,
                       com.beefamily.album.repository.PhotoRepository photoRepository,
                       FamilyAccessGuard guard) {
        this.babyRepository = babyRepository;
        this.milestoneRepository = milestoneRepository;
        this.growthRecordRepository = growthRecordRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.guard = guard;
    }

    /**
     * 家庭宝宝列表（含里程碑数、最新成长记录）。
     *
     * @param familyId 家庭 id
     * @return 宝宝列表
     */
    @Transactional(readOnly = true)
    public List<BabyResp> listByFamily(Long familyId) {
        List<Baby> babies = babyRepository.findByFamilyIdOrderById(familyId);
        return babies.stream().map(this::toResp).toList();
    }

    /**
     * 当前家庭的宝宝列表（登录引导 / TokenResp / MeResp 使用）。
     *
     * @return 宝宝列表
     */
    @Transactional(readOnly = true)
    public List<BabyResp> listOfCurrentFamily() {
        return listByFamily(guard.familyId());
    }

    /**
     * 宝宝详情。
     *
     * @param id 宝宝 id
     * @return 详情
     */
    @Transactional(readOnly = true)
    public BabyDetailResp detail(Long id) {
        Long familyId = guard.familyId();
        Baby baby = babyRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        GrowthRecordResp latestGrowth = latestGrowth(baby);
        MilestoneResp latestMilestone = milestoneRepository
                .findFirstByBabyIdOrderByAchievedAtDescIdDesc(baby.getId())
                .map(milestone -> MilestoneMapper.toResp(milestone, baby.getName(), baby.getBirthday(),
                        photoThumbKey(milestone), nicknameOf(milestone.getCreatedBy())))
                .orElse(null);
        return BabyMapper.toDetailResp(baby, milestoneRepository.countByBabyId(baby.getId()), latestGrowth,
                latestMilestone, growthRecordRepository.countByBabyId(baby.getId()));
    }

    /**
     * 新建宝宝档案。
     *
     * @param req 请求
     * @return 宝宝响应
     */
    @Transactional
    public BabyResp create(BabyReq req) {
        Long familyId = guard.familyId();
        long existing = babyRepository.countByFamilyId(familyId);
        if (existing >= Limits.BABY_LIMIT) {
            throw new BusinessException(ErrorCode.BABY_LIMIT);
        }
        Baby baby = new Baby();
        baby.setFamilyId(familyId);
        applyFields(baby, req.name(), req.gender(), req.birthday(), req.avatarUrl(), req.birthHeightCm(),
                req.birthWeightKg());
        Baby saved = babyRepository.save(baby);
        log.info("baby created id={} familyId={}", saved.getId(), familyId);
        return toResp(saved);
    }

    /**
     * 更新宝宝档案（全量提交）。
     *
     * @param id  宝宝 id
     * @param req 请求
     * @return 宝宝响应
     */
    @Transactional
    public BabyResp update(Long id, BabyUpdateReq req) {
        Long familyId = guard.familyId();
        Baby baby = babyRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        applyFields(baby, req.name(), req.gender(), req.birthday(), req.avatarUrl(), req.birthHeightCm(),
                req.birthWeightKg());
        return toResp(babyRepository.save(baby));
    }

    /**
     * 删除宝宝档案（仅 owner）。
     *
     * @param id 宝宝 id
     */
    @Transactional
    public void delete(Long id) {
        guard.assertOwner();
        Long familyId = guard.familyId();
        Baby baby = babyRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        babyRepository.delete(baby);
        log.info("baby deleted id={} familyId={}", id, familyId);
    }

    /**
     * 取当前家庭内的宝宝，否则抛 40301。
     *
     * @param babyId 宝宝 id
     * @return 宝宝实体
     */
    @Transactional(readOnly = true)
    public Baby requireBaby(Long babyId) {
        return babyRepository.findByIdAndFamilyId(babyId, guard.familyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
    }

    /**
     * 组装单个宝宝响应（供本服务与其他服务复用）。
     *
     * @param baby 宝宝实体
     * @return 响应
     */
    @Transactional(readOnly = true)
    public BabyResp toResp(Baby baby) {
        return BabyMapper.toResp(baby, milestoneRepository.countByBabyId(baby.getId()), latestGrowth(baby));
    }

    /**
     * 批量查昵称。
     *
     * @param userIds 用户 id 集合
     * @return id → 昵称
     */
    @Transactional(readOnly = true)
    public Map<Long, String> nicknames(Collection<Long> userIds) {
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
     * @return 昵称，不存在时返回 null
     */
    @Transactional(readOnly = true)
    public String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getNickname).orElse(null);
    }

    /**
     * 宝宝最新成长记录。
     *
     * @param baby 宝宝实体
     * @return 响应，无记录时 null
     */
    private GrowthRecordResp latestGrowth(Baby baby) {
        return growthRecordRepository.findFirstByBabyIdOrderByRecordedAtDesc(baby.getId())
                .map(record -> GrowthMapper.toResp(record, baby.getName(), nicknameOf(record.getCreatedBy())))
                .orElse(null);
    }

    /**
     * 里程碑配图缩略图 key。
     *
     * @param milestone 里程碑
     * @return key，无配图或照片已删时为 null
     */
    private String photoThumbKey(Milestone milestone) {
        if (milestone.getPhotoId() == null) {
            return null;
        }
        return photoRepository.findByIdAndFamilyId(milestone.getPhotoId(), milestone.getFamilyId())
                .map(com.beefamily.album.domain.Photo::getThumbKey)
                .orElse(null);
    }

    /**
     * 应用可编辑字段。
     *
     * @param baby          实体
     * @param name          昵称
     * @param gender        性别
     * @param birthday      生日
     * @param avatarUrl     头像
     * @param birthHeightCm 出生身高
     * @param birthWeightKg 出生体重
     */
    private void applyFields(Baby baby, String name, com.beefamily.album.domain.enums.Gender gender,
                             java.time.LocalDate birthday, String avatarUrl, java.math.BigDecimal birthHeightCm,
                             java.math.BigDecimal birthWeightKg) {
        baby.setName(name.trim());
        baby.setGender(gender);
        baby.setBirthday(birthday);
        baby.setAvatarUrl(avatarUrl);
        baby.setBirthHeightCm(birthHeightCm);
        baby.setBirthWeightKg(birthWeightKg);
    }
}
