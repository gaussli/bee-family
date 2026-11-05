package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.Milestone;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.milestone.MilestoneReq;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.mapper.MilestoneMapper;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.SecurityUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 里程碑服务。
 *
 * <p>{@code familyId} 一律取自 {@code UserPrincipal}，**不接受前端传参**：
 * 客户端传 familyId 会让「越权写别人家庭」成为一个请求参数的问题。</p>
 *
 * <p>配图 / 归属宝宝都必须与里程碑同家庭，否则 40301 —— 否则可以把别人的照片挂到自己的里程碑上，
 * 造成跨家庭数据泄露。</p>
 */
@Service
public class MilestoneService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final BabyRepository babyRepository;
    private final PhotoRepository photoRepository;
    private final BabyService babyService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param milestoneRepository 里程碑仓储
     * @param babyRepository      宝宝仓储
     * @param photoRepository     照片仓储
     * @param babyService         宝宝服务（昵称 / 归属校验）
     * @param guard               家庭闸门
     */
    public MilestoneService(MilestoneRepository milestoneRepository, BabyRepository babyRepository,
                            PhotoRepository photoRepository, BabyService babyService, FamilyAccessGuard guard) {
        this.milestoneRepository = milestoneRepository;
        this.babyRepository = babyRepository;
        this.photoRepository = photoRepository;
        this.babyService = babyService;
        this.guard = guard;
    }

    /**
     * 里程碑分页（可按宝宝过滤，达成日期倒序）。
     *
     * @param babyId 宝宝 id，可空
     * @param query  分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<MilestoneResp> list(Long babyId, PageQuery query) {
        Long familyId = guard.familyId();
        PageQuery normalized = query.normalize(PageQuery.DEFAULT_LIST_SIZE);
        PageRequest pageRequest = PageRequest.of(normalized.page(), normalized.size());
        Page<Milestone> page = babyId == null
                ? milestoneRepository.findByFamilyIdOrderByAchievedAtDescIdDesc(familyId, pageRequest)
                : milestoneRepository.findByFamilyIdAndBabyIdOrderByAchievedAtDescIdDesc(familyId, babyId,
                        pageRequest);
        List<MilestoneResp> items = toRespBatch(page.getContent(), familyId);
        return PageResponse.of(items, normalized, page.getTotalElements());
    }

    /**
     * 新建里程碑。
     *
     * @param req 请求
     * @return 响应
     */
    @Transactional
    public MilestoneResp create(MilestoneReq req) {
        Long familyId = guard.familyId();
        validateReferences(req, familyId);
        Milestone milestone = new Milestone();
        milestone.setFamilyId(familyId);
        milestone.setCreatedBy(SecurityUtils.currentUserId());
        applyFields(milestone, req);
        Milestone saved = milestoneRepository.save(milestone);
        log.info("milestone created id={} familyId={}", saved.getId(), familyId);
        return toRespBatch(List.of(saved), familyId).get(0);
    }

    /**
     * 更新里程碑（全量提交）。
     *
     * @param id  里程碑 id
     * @param req 请求
     * @return 响应
     */
    @Transactional
    public MilestoneResp update(Long id, MilestoneReq req) {
        Long familyId = guard.familyId();
        Milestone milestone = milestoneRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        validateReferences(req, familyId);
        applyFields(milestone, req);
        Milestone saved = milestoneRepository.save(milestone);
        return toRespBatch(List.of(saved), familyId).get(0);
    }

    /**
     * 删除里程碑。
     *
     * @param id 里程碑 id
     */
    @Transactional
    public void delete(Long id) {
        Milestone milestone = milestoneRepository.findByIdAndFamilyId(id, guard.familyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        milestoneRepository.delete(milestone);
    }

    /**
     * 批量转响应（时间轴 / 搜索复用，避免逐条查宝宝与用户）。
     *
     * @param milestones 里程碑列表
     * @param familyId   家庭 id
     * @return 响应列表
     */
    @Transactional(readOnly = true)
    public List<MilestoneResp> toRespBatch(Collection<Milestone> milestones, Long familyId) {
        if (milestones == null || milestones.isEmpty()) {
            return List.of();
        }
        List<Long> babyIds = milestones.stream().map(Milestone::getBabyId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        Map<Long, Baby> babies = new HashMap<>();
        if (!babyIds.isEmpty()) {
            for (Baby baby : babyRepository.findByIdInAndFamilyId(babyIds, familyId)) {
                babies.put(baby.getId(), baby);
            }
        }
        List<Long> photoIds = milestones.stream().map(Milestone::getPhotoId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        Map<Long, String> thumbs = new HashMap<>();
        if (!photoIds.isEmpty()) {
            for (Photo photo : photoRepository.findByIdInAndFamilyId(photoIds, familyId)) {
                thumbs.put(photo.getId(), photo.getThumbKey());
            }
        }
        Map<Long, String> nicknames = babyService.nicknames(milestones.stream()
                .map(Milestone::getCreatedBy).toList());
        return milestones.stream().map(milestone -> {
            Baby baby = milestone.getBabyId() == null ? null : babies.get(milestone.getBabyId());
            return MilestoneMapper.toResp(milestone,
                    baby == null ? null : baby.getName(),
                    baby == null ? null : baby.getBirthday(),
                    milestone.getPhotoId() == null ? null : thumbs.get(milestone.getPhotoId()),
                    nicknames.get(milestone.getCreatedBy()));
        }).toList();
    }

    /**
     * 校验 babyId / photoId 归属同一家庭。
     *
     * @param req      请求
     * @param familyId 家庭 id
     */
    private void validateReferences(MilestoneReq req, Long familyId) {
        if (req.babyId() != null) {
            babyRepository.findByIdAndFamilyId(req.babyId(), familyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        }
        if (req.photoId() != null) {
            photoRepository.findByIdAndFamilyId(req.photoId(), familyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
        }
    }

    /**
     * 应用可编辑字段。
     *
     * @param milestone 实体
     * @param req       请求
     */
    private void applyFields(Milestone milestone, MilestoneReq req) {
        milestone.setBabyId(req.babyId());
        milestone.setTitle(req.title().trim());
        milestone.setDescription(req.description());
        milestone.setAchievedAt(req.achievedAt());
        milestone.setPhotoId(req.photoId());
    }
}
