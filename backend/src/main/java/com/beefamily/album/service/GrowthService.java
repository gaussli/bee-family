package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.dto.growth.CurvePointResp;
import com.beefamily.album.dto.growth.GrowthCurveResp;
import com.beefamily.album.dto.growth.GrowthRecordReq;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.mapper.GrowthMapper;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.SecurityUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成长记录与成长曲线服务。
 *
 * <p><b>UPSERT 语义</b>（PRD P0-04①）：同一 {@code (babyId, recordedAt)} 只允许一条记录，POST 命中既有行时
 * 覆盖测量值并返回既有 id，**不返回 409**。这样前端保存抽屉后无需再弹「是否覆盖」的二次确认。</p>
 *
 * <p><b>区间语义</b>（T07 关键约束②）：{@code 3m/6m/12m} 表示「从该宝宝**最新一条记录**的日期往前推」，
 * 而不是「从今天往前推」—— 用户三个月没测量时，从今天推会让曲线整段空掉。</p>
 */
@Service
public class GrowthService {

    private static final Logger log = LoggerFactory.getLogger(GrowthService.class);

    /** 允许的曲线区间。 */
    private static final Set<String> SUPPORTED_RANGES = Set.of("3m", "6m", "12m", "all");

    private final GrowthRecordRepository growthRecordRepository;
    private final BabyService babyService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param growthRecordRepository 成长记录仓储
     * @param babyService            宝宝服务（取宝宝与昵称）
     * @param guard                  家庭闸门
     */
    public GrowthService(GrowthRecordRepository growthRecordRepository, BabyService babyService,
                         FamilyAccessGuard guard) {
        this.growthRecordRepository = growthRecordRepository;
        this.babyService = babyService;
        this.guard = guard;
    }

    /**
     * 某宝宝的成长记录分页（日期倒序）。
     *
     * @param babyId 宝宝 id
     * @param query  分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<GrowthRecordResp> list(Long babyId, PageQuery query) {
        Baby baby = babyService.requireBaby(babyId);
        PageQuery normalized = query.normalize(PageQuery.DEFAULT_LIST_SIZE);
        Page<GrowthRecord> page = growthRecordRepository.findByBabyIdOrderByRecordedAtDesc(baby.getId(),
                PageRequest.of(normalized.page(), normalized.size()));
        Map<Long, String> nicknames = babyService.nicknames(page.getContent().stream()
                .map(GrowthRecord::getCreatedBy).toList());
        List<GrowthRecordResp> items = page.getContent().stream()
                .map(record -> GrowthMapper.toResp(record, baby.getName(), nicknames.get(record.getCreatedBy())))
                .toList();
        return PageResponse.of(items, normalized, page.getTotalElements());
    }

    /**
     * 新增或覆盖某日期的测量记录（UPSERT）。
     *
     * @param babyId 宝宝 id
     * @param req    请求
     * @return 记录响应
     */
    @Transactional
    public GrowthRecordResp upsert(Long babyId, GrowthRecordReq req) {
        Baby baby = babyService.requireBaby(babyId);
        validateMeasures(req);
        GrowthRecord record = growthRecordRepository
                .findByBabyIdAndRecordedAt(baby.getId(), req.recordedAt())
                .orElse(null);
        boolean created = record == null;
        if (created) {
            record = new GrowthRecord();
            record.setBabyId(baby.getId());
            record.setCreatedBy(SecurityUtils.currentUserId());
        }
        applyMeasures(record, req);
        GrowthRecord saved = growthRecordRepository.save(record);
        log.info("growth record {} id={} babyId={} recordedAt={}", created ? "created" : "updated",
                saved.getId(), baby.getId(), saved.getRecordedAt());
        return GrowthMapper.toResp(saved, baby.getName(), babyService.nicknameOf(saved.getCreatedBy()));
    }

    /**
     * 更新记录（PATCH 全量提交）。
     *
     * @param id  记录 id
     * @param req 请求
     * @return 记录响应
     */
    @Transactional
    public GrowthRecordResp update(Long id, GrowthRecordReq req) {
        validateMeasures(req);
        GrowthRecord record = requireRecord(id);
        // 改到已存在的日期会撞唯一键：提前给出可操作提示，而不是抛 SQL 异常
        growthRecordRepository.findByBabyIdAndRecordedAt(record.getBabyId(), req.recordedAt())
                .filter(other -> !other.getId().equals(record.getId()))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.INVALID_PARAM,
                            "该日期已有测量记录，请直接编辑那一天的数据");
                });
        applyMeasures(record, req);
        GrowthRecord saved = growthRecordRepository.save(record);
        Baby baby = babyService.requireBaby(saved.getBabyId());
        return GrowthMapper.toResp(saved, baby.getName(), babyService.nicknameOf(saved.getCreatedBy()));
    }

    /**
     * 删除记录。
     *
     * @param id 记录 id
     */
    @Transactional
    public void delete(Long id) {
        growthRecordRepository.delete(requireRecord(id));
    }

    /**
     * 成长曲线。
     *
     * @param babyId 宝宝 id
     * @param range  区间：3m / 6m / 12m / all
     * @return 曲线数据
     */
    @Transactional(readOnly = true)
    public GrowthCurveResp curve(Long babyId, String range) {
        String normalizedRange = normalizeRange(range);
        Baby baby = babyService.requireBaby(babyId);
        List<GrowthRecord> allAsc = growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(baby.getId());
        if (allAsc.isEmpty()) {
            return new GrowthCurveResp(baby.getId(), baby.getName(), baby.getBirthday(), normalizedRange,
                    null, null, List.of(), null, new com.beefamily.album.dto.growth.GrowthDeltaResp(null, null, null));
        }
        LocalDate latestDate = allAsc.get(allAsc.size() - 1).getRecordedAt();
        List<GrowthRecord> selected;
        LocalDate from;
        LocalDate to;
        if ("all".equals(normalizedRange)) {
            selected = allAsc;
            from = allAsc.get(0).getRecordedAt();
            to = latestDate;
        } else {
            from = latestDate.minusMonths(parseMonths(normalizedRange));
            to = latestDate;
            selected = filterBetween(allAsc, from, to);
        }
        List<CurvePointResp> points = GrowthMapper.toCurvePoints(selected, baby.getBirthday());
        GrowthRecord latest = selected.isEmpty() ? null : selected.get(selected.size() - 1);
        GrowthRecord previous = selected.size() < 2 ? null : selected.get(selected.size() - 2);
        GrowthMapper.GrowthDelta delta = GrowthMapper.toDelta(latest, previous);
        GrowthCurveResp.LatestPoint latestPoint = latest == null ? null
                : new GrowthCurveResp.LatestPoint(latest.getRecordedAt(), latest.getHeightCm(),
                        latest.getWeightKg(), latest.getHeadCm());
        return new GrowthCurveResp(baby.getId(), baby.getName(), baby.getBirthday(), normalizedRange, from, to,
                points, latestPoint, new com.beefamily.album.dto.growth.GrowthDeltaResp(
                        delta.heightCm(), delta.weightKg(), delta.headCm()));
    }

    /**
     * 读取当前家庭内的记录，否则 40301。
     *
     * @param id 记录 id
     * @return 记录实体
     */
    private GrowthRecord requireRecord(Long id) {
        return growthRecordRepository.findByIdAndFamilyId(id, guard.familyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS));
    }

    /**
     * 校验：至少一项测量值非空。
     *
     * @param req 请求
     */
    private void validateMeasures(GrowthRecordReq req) {
        if (req.isEmptyMeasure()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "请至少填写身高 / 体重 / 头围中的一项");
        }
    }

    /**
     * 应用测量字段。
     *
     * @param record 实体
     * @param req    请求
     */
    private void applyMeasures(GrowthRecord record, GrowthRecordReq req) {
        record.setRecordedAt(req.recordedAt());
        record.setHeightCm(req.heightCm());
        record.setWeightKg(req.weightKg());
        record.setHeadCm(req.headCm());
        record.setNote(req.note());
    }

    /**
     * 校验并规范化区间参数。
     *
     * @param range 原始参数
     * @return 规范化结果
     */
    private String normalizeRange(String range) {
        String value = range == null ? "" : range.trim().toLowerCase();
        if (!SUPPORTED_RANGES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "参数 range 取值非法，允许 3m/6m/12m/all");
        }
        return value;
    }

    /**
     * 把 {@code 3m/6m/12m} 解析为月数。
     *
     * @param range 区间
     * @return 月数
     */
    private int parseMonths(String range) {
        return Integer.parseInt(range.substring(0, range.length() - 1));
    }

    /**
     * 按日期区间过滤（升序输入，升序输出）。
     *
     * @param records 记录
     * @param from    起始
     * @param to      结束
     * @return 过滤结果
     */
    private List<GrowthRecord> filterBetween(List<GrowthRecord> records, LocalDate from, LocalDate to) {
        List<GrowthRecord> result = new ArrayList<>();
        for (GrowthRecord record : records) {
            if (!record.getRecordedAt().isBefore(from) && !record.getRecordedAt().isAfter(to)) {
                result.add(record);
            }
        }
        return result;
    }
}
