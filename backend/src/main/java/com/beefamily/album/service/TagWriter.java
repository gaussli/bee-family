package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Tag;
import com.beefamily.album.repository.TagRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签行的写入者：**独立事务**里「幂等插入 + 回查」，返回标签行。
 *
 * <p><b>为什么必须独立事务（不能是 TagService 里的私有方法）</b>：原实现在调用方事务里
 * 直接 {@code saveAndFlush}，撞上 {@code uk_tag_family_name} 时唯一键冲突虽然被
 * {@code catch (DataIntegrityViolationException)} 接住，但**失败的 flush 已经把 id 为 null 的
 * {@link Tag} 留在持久化上下文里**；外层事务提交时 Hibernate 又 flush 一次这个游离实例，抛
 * {@code AssertionFailure: null id in com.beefamily.album.domain.Tag entry}，
 * 把兜底逻辑一起打穿 —— 10 个并发请求里 3~5 个 500（QA P1-②）。</p>
 *
 * <p><b>为什么是 {@code ON DUPLICATE KEY UPDATE} 而不是「insert + catch」</b>：见
 * {@link TagRepository#insertIfAbsent}。冲突对并发打标签是**正常路径**，不应该靠异常来表达，
 * 否则每次并发都留一条 {@code ERROR Duplicate entry} 日志。</p>
 *
 * <p><b>为什么回查也要在这个新事务里</b>：调用方事务在 MySQL REPEATABLE READ 下的读视图
 * 早于对手提交，自己查是查不到的。新事务的读视图在本事务首次读之前建立，能看到对手已提交的行；
 * 若这一行是本事务刚插入的，本事务自己的写更是可见。两种情况都拿得到。</p>
 *
 * <p>注意：本类是独立 Bean —— 同类内自调用不经过 Spring 事务代理，{@code REQUIRES_NEW} 会失效
 * （同一陷阱见 {@code §3.5}）。</p>
 */
@Service
public class TagWriter {

    private static final Logger log = LoggerFactory.getLogger(TagWriter.class);

    /** 幂等插入后回查的最大尝试次数（含首次）。 */
    private static final int MAX_ATTEMPTS = 2;

    private final TagRepository tagRepository;

    /**
     * 构造。
     *
     * @param tagRepository 标签仓储
     */
    public TagWriter(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 幂等地拿到「家庭内名为 {@code name} 的标签行」：不存在则创建，存在则复用。
     *
     * <p><b>极端竞态兜底</b>：{@code on duplicate key update} 之后紧接着的 SELECT 理论上一定能
     * 读到（本事务自己的写自己可见，对手已提交也可见），但如果对手事务在这两步之间回滚并**再次**
     * 抢先提交、或连接层做了读写分离路由，仍可能读空。这时重试一次；两次都读空才抛业务异常，
     * 且 WARN 里带 {@code familyId/name}，便于区分「真的写不进去」和「读到了陈旧路由」。</p>
     *
     * @param familyId 家庭 id
     * @param name     标签名（已规范化）
     * @return 标签实体（游离态，调用方只应读取 {@code id}）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Tag createOrFind(Long familyId, String name) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            tagRepository.insertIfAbsent(familyId, name);
            Optional<Tag> found = tagRepository.findByFamilyIdAndName(familyId, name);
            if (found.isPresent()) {
                return found.get();
            }
            log.warn("tag upsert then lookup missed familyId={} name={} attempt={}", familyId, name, attempt);
        }
        log.error("tag upsert failed familyId={} name={} attempts={}", familyId, name, MAX_ATTEMPTS);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "标签写入失败，请重试");
    }
}
