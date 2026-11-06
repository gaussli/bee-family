package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Family;
import com.beefamily.album.domain.FamilyMember;
import com.beefamily.album.domain.User;
import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.dto.family.FamilyResp;
import com.beefamily.album.dto.family.MemberResp;
import com.beefamily.album.mapper.FamilyMapper;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.FamilyMemberRepository;
import com.beefamily.album.repository.FamilyRepository;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.PhotoTagRepository;
import com.beefamily.album.repository.TagRepository;
import com.beefamily.album.repository.UserRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.SecurityUtils;
import com.beefamily.album.support.DateTimes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 家庭空间与成员服务。
 *
 * <p><b>删家庭为什么按 9 步显式删除而不交给级联</b>（§6.4 注）：</p>
 * <ol>
 *   <li>InnoDB 未承诺多路径级联的执行顺序，{@code CASCADE} 与 {@code SET NULL} 交织时行为依赖内部实现；</li>
 *   <li>需要逐表记录删除行数（审计日志）；</li>
 *   <li>需要在 {@code afterCommit} 拿到该家庭全部 storage key 以清理磁盘 —— 级联删完后已无从查询。</li>
 * </ol>
 * <p>FK 的 CASCADE 保留为「绕过 service 直接删库」时的兜底，不作为业务路径依赖。</p>
 */
@Service
public class FamilyService {

    private static final Logger log = LoggerFactory.getLogger(FamilyService.class);

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;
    private final MilestoneRepository milestoneRepository;
    private final GrowthRecordRepository growthRecordRepository;
    private final AlbumRepository albumRepository;
    private final BabyRepository babyRepository;
    private final TagRepository tagRepository;
    private final StorageService storageService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param familyRepository       家庭仓储
     * @param familyMemberRepository 成员仓储
     * @param userRepository         用户仓储
     * @param photoRepository        照片仓储
     * @param photoTagRepository     照片标签仓储
     * @param milestoneRepository    里程碑仓储
     * @param growthRecordRepository 成长记录仓储
     * @param albumRepository        相册仓储
     * @param babyRepository         宝宝仓储
     * @param tagRepository          标签仓储
     * @param storageService         存储服务（afterCommit 清磁盘）
     * @param guard                  家庭闸门
     */
    public FamilyService(FamilyRepository familyRepository,
                         FamilyMemberRepository familyMemberRepository,
                         UserRepository userRepository,
                         PhotoRepository photoRepository,
                         PhotoTagRepository photoTagRepository,
                         MilestoneRepository milestoneRepository,
                         GrowthRecordRepository growthRecordRepository,
                         AlbumRepository albumRepository,
                         BabyRepository babyRepository,
                         TagRepository tagRepository,
                         StorageService storageService,
                         FamilyAccessGuard guard) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.milestoneRepository = milestoneRepository;
        this.growthRecordRepository = growthRecordRepository;
        this.albumRepository = albumRepository;
        this.babyRepository = babyRepository;
        this.tagRepository = tagRepository;
        this.storageService = storageService;
        this.guard = guard;
    }

    /**
     * 当前家庭信息。
     *
     * @return 家庭响应
     */
    @Transactional(readOnly = true)
    public FamilyResp getFamily() {
        Family family = requireFamily();
        return buildResp(family);
    }

    /**
     * 修改家庭名（仅 owner）。
     *
     * @param name 新家庭名
     * @return 家庭响应
     */
    @Transactional
    public FamilyResp updateFamily(String name) {
        guard.assertOwner();
        Family family = requireFamily();
        family.setName(name.trim());
        return buildResp(familyRepository.save(family));
    }

    /**
     * 删除家庭（仅 owner，需输入家庭名确认）。
     *
     * <p>删除顺序（§6.4 注）：milestone → photo_tag → photo → growth_record → album → baby → tag →
     * family_member → family；磁盘目录在事务提交后删除。</p>
     *
     * @param confirmName 用户输入的家庭名
     */
    @Transactional
    public void deleteFamily(String confirmName) {
        guard.assertOwner();
        Family family = requireFamily();
        if (!family.getName().equals(confirmName == null ? "" : confirmName.trim())) {
            throw new BusinessException(ErrorCode.CONFIRM_MISMATCH);
        }
        Long familyId = family.getId();
        List<String> storageKeys = collectStorageKeys(familyId);

        int milestones = milestoneRepository.deleteByFamilyId(familyId);
        int photoTags = photoTagRepository.deleteByFamilyId(familyId);
        int photos = photoRepository.deleteByFamilyId(familyId);
        int growthRecords = growthRecordRepository.deleteByFamilyId(familyId);
        int albums = albumRepository.deleteByFamilyId(familyId);
        int babies = babyRepository.deleteByFamilyId(familyId);
        int tags = tagRepository.deleteByFamilyId(familyId);
        int members = familyMemberRepository.deleteByFamilyId(familyId);
        familyRepository.delete(family);

        log.info("family deleted familyId={} milestones={} photoTags={} photos={} growth={} albums={} babies={} "
                        + "tags={} members={}", familyId, milestones, photoTags, photos, growthRecords, albums,
                babies, tags, members);

        runAfterCommit(() -> {
            storageService.deleteQuietly(storageKeys);
            storageService.deleteDirectory(familyId);
        });
    }

    /**
     * 成员列表（含每人上传照片数）。
     *
     * @return 成员列表
     */
    @Transactional(readOnly = true)
    public List<MemberResp> listMembers() {
        Long familyId = guard.familyId();
        List<FamilyMember> members = familyMemberRepository.findByFamilyIdOrderByJoinedAt(familyId);
        Map<Long, User> users = new HashMap<>();
        for (User user : userRepository.findAllById(members.stream().map(FamilyMember::getUserId).toList())) {
            users.put(user.getId(), user);
        }
        List<MemberResp> result = new ArrayList<>(members.size());
        for (FamilyMember member : members) {
            long uploaded = photoRepository.countByUploadedByAndFamilyId(member.getUserId(), familyId);
            result.add(FamilyMapper.toMemberResp(member, users.get(member.getUserId()), uploaded));
        }
        return result;
    }

    /**
     * 移除成员（仅 owner；不能移除 owner 或自己）。
     *
     * <p>只删成员关系：不删该用户上传的照片、不删 user —— 家庭成员离开不应带走共同记忆；
     * 同时把该用户的 token 置为失效，避免其继续访问家庭数据。</p>
     *
     * @param userId 目标用户 id
     */
    @Transactional
    public void removeMember(Long userId) {
        guard.assertOwner();
        Long familyId = guard.familyId();
        FamilyMember target = familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target.getRole() == Role.owner || target.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_REMOVABLE);
        }
        familyMemberRepository.deleteMember(familyId, userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenValidAfter(DateTimes.now());
            userRepository.save(user);
        });
        log.info("member removed familyId={} userId={}", familyId, userId);
    }

    /**
     * 读取当前家庭实体。
     *
     * @return 家庭实体
     */
    private Family requireFamily() {
        Long familyId = guard.familyId();
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 组装家庭响应（邀请码仅 owner 可见）。
     *
     * @param family 家庭实体
     * @return 响应
     */
    private FamilyResp buildResp(Family family) {
        String ownerNickname = userRepository.findById(family.getOwnerId()).map(User::getNickname).orElse(null);
        long memberCount = familyMemberRepository.countByFamilyId(family.getId());
        boolean includeInviteCode = guard.isOwner();
        return FamilyMapper.toResp(family, ownerNickname, memberCount, includeInviteCode);
    }


    /**
     * 收集家庭全部照片的三档 key（删家庭时用于清理磁盘）。
     *
     * @param familyId 家庭 id
     * @return key 列表
     */
    private List<String> collectStorageKeys(Long familyId) {
        List<String> keys = new ArrayList<>();
        for (Object[] row : photoRepository.findStorageKeysByFamilyId(familyId)) {
            for (Object value : row) {
                if (value instanceof String key && !key.isBlank()) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /**
     * 注册事务提交后动作。
     *
     * <p>文件删除必须在事务提交后执行：若事务回滚，DB 仍引用这些文件，提前删除会造成不可恢复的破图（§8.4）。</p>
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
