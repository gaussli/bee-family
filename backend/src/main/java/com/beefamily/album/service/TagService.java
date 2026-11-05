package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.PhotoTag;
import com.beefamily.album.domain.PhotoTagId;
import com.beefamily.album.domain.Tag;
import com.beefamily.album.dto.search.TagResp;
import com.beefamily.album.repository.PhotoTagRepository;
import com.beefamily.album.repository.TagRepository;
import com.beefamily.album.support.DateTimes;
import com.beefamily.album.support.Limits;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签服务：标签解析 / 差分写入 / 列表查询。
 *
 * <p><b>差分算法</b>（T11 关键约束⑤）：请求的标签集合是**完整集合**，
 * 新增 = 请求 − 已有、删除关联 = 已有 − 请求；{@code tag} 行只增不删（孤儿标签留待 P1 清理）。
 * 这样既不会给未改动的标签刷新 {@code created_at}（影响「最近使用」排序），也不会残留已移除的关联。</p>
 */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagWriter tagWriter;

    /**
     * 构造服务。
     *
     * @param tagRepository      标签仓储
     * @param photoTagRepository 关联仓储
     * @param tagWriter          标签创建者（独立事务，见 {@link TagWriter} 的类注释）
     */
    public TagService(TagRepository tagRepository, PhotoTagRepository photoTagRepository, TagWriter tagWriter) {
        this.tagRepository = tagRepository;
        this.photoTagRepository = photoTagRepository;
        this.tagWriter = tagWriter;
    }

    /**
     * 标签列表：有 {@code q} 时前缀匹配（按名称升序），无 {@code q} 时按最近使用倒序。
     *
     * @param familyId 家庭 id
     * @param keyword  关键词（可空）
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<TagResp> list(Long familyId, String keyword) {
        PageRequest limit = PageRequest.of(0, Limits.TAG_LIST_LIMIT);
        String trimmed = keyword == null ? "" : keyword.trim();
        List<Object[]> rows;
        if (trimmed.isEmpty()) {
            rows = tagRepository.findUsage(familyId, limit);
        } else {
            rows = tagRepository.findUsageByPrefix(familyId, trimmed, limit);
        }
        List<TagResp> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new TagResp(((Number) row[0]).longValue(), (String) row[1],
                    ((Number) row[2]).longValue(), toLocalDateTime(row[3])));
        }
        return result;
    }

    /**
     * 把照片标签整体替换为目标集合（差分写入）。
     *
     * @param familyId 家庭 id
     * @param photoId  照片 id
     * @param names    目标标签名集合（完整集合语义；空集合即清空）
     * @return 写入后的标签名（去重、保持入参顺序）
     */
    @Transactional
    public List<String> applyToPhoto(Long familyId, Long photoId, List<String> names) {
        List<String> normalized = normalizeNames(names);
        Map<String, Tag> targets = resolveOrCreate(familyId, normalized);

        List<PhotoTag> existingRelations = photoTagRepository.findByPhotoIdIn(List.of(photoId));
        Set<Long> existingTagIds = new LinkedHashSet<>();
        for (PhotoTag relation : existingRelations) {
            existingTagIds.add(relation.getId().getTagId());
        }
        Set<Long> targetTagIds = new LinkedHashSet<>();
        for (Tag tag : targets.values()) {
            targetTagIds.add(tag.getId());
        }

        List<PhotoTagId> toDelete = new ArrayList<>();
        for (Long tagId : existingTagIds) {
            if (!targetTagIds.contains(tagId)) {
                toDelete.add(new PhotoTagId(photoId, tagId));
            }
        }
        if (!toDelete.isEmpty()) {
            photoTagRepository.deleteAllById(toDelete);
        }

        // 关联插入用幂等语句（联合主键冲突收敛成 1 行）：同一张照片被并发打同一个标签时，
        // saveAll 会抛 DataIntegrityViolationException → 500，而对「打标签」来说冲突是正常语义
        for (Long tagId : targetTagIds) {
            if (!existingTagIds.contains(tagId)) {
                photoTagRepository.insertIfAbsent(photoId, tagId);
            }
        }
        return new ArrayList<>(targets.keySet());
    }

    /**
     * 批量查询照片的标签名（时间轴 / 列表避免 N+1）。
     *
     * @param photoIds 照片 id 集合
     * @return photoId → 标签名列表（按标签 id 升序，保证稳定）
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> tagNamesByPhotoIds(Collection<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return Map.of();
        }
        List<PhotoTag> relations = photoTagRepository.findByPhotoIdIn(photoIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = new LinkedHashSet<>();
        for (PhotoTag relation : relations) {
            tagIds.add(relation.getId().getTagId());
        }
        Map<Long, String> tagNames = new HashMap<>();
        for (Tag tag : tagRepository.findAllById(tagIds)) {
            tagNames.put(tag.getId(), tag.getName());
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (PhotoTag relation : relations) {
            String name = tagNames.get(relation.getId().getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getId().getPhotoId(), key -> new ArrayList<>()).add(name);
            }
        }
        result.values().forEach(list -> list.sort(String::compareTo));
        return result;
    }

    /**
     * 解析标签名 → 标签实体，缺失则创建。
     *
     * <p><b>并发去重交给数据库</b>：{@code uk_tag_family_name} 保证家庭内同名标签只有一行。
     * 这里把「插入 + 回查」整体委托给 {@link TagWriter} 的 {@code REQUIRES_NEW} 独立事务：
     * 一是避免失败的 flush 把 id 为 null 的实体留在调用方 session 里（{@code AssertionFailure}），
     * 二是新事务的读视图能看到并发对手刚提交的行（REPEATABLE READ 下调用方快照看不到）。</p>
     *
     * @param familyId 家庭 id
     * @param names    已规范化的标签名（有序）
     * @return 名称 → 实体（保持入参顺序）
     */
    @Transactional
    public Map<String, Tag> resolveOrCreate(Long familyId, List<String> names) {
        Map<String, Tag> result = new LinkedHashMap<>();
        for (String name : names) {
            Tag tag = tagRepository.findByFamilyIdAndName(familyId, name).orElse(null);
            if (tag == null) {
                tag = tagWriter.createOrFind(familyId, name);
            }
            result.put(name, tag);
        }
        return result;
    }

    /**
     * 名称规范化：去首尾空格、去空值、去重（保序）、校验长度。
     *
     * @param names 原始名称集合
     * @return 规范化后的名称
     */
    public List<String> normalizeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > Limits.TAG_NAME_MAX_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_PARAM,
                        "单个标签不能超过 " + Limits.TAG_NAME_MAX_LENGTH + " 个字符");
            }
            unique.add(name);
        }
        return new ArrayList<>(unique);
    }

    /**
     * 查询家庭内某标签（供搜索/测试使用）。
     *
     * @param familyId 家庭 id
     * @param name     标签名
     * @return 标签
     */
    @Transactional(readOnly = true)
    public Optional<Tag> find(Long familyId, String name) {
        return tagRepository.findByFamilyIdAndName(familyId, name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * JDBC 时间对象 → {@code LocalDateTime}。
     *
     * @param value 原始值
     * @return 本地时间，null 安全
     */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return null;
    }

    /**
     * 空集合常量（避免调用方判空）。
     *
     * @return 空标签名列表
     */
    public List<String> emptyNames() {
        return Collections.emptyList();
    }
}
