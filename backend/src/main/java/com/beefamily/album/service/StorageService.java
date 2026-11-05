package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.config.StorageProperties;
import com.beefamily.album.support.DateTimes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 存储服务：key 生成、写入、读取、删除与路径穿越防护。
 *
 * <p><b>key 组成完全由服务端决定</b>：{@code {familyId}/{yyyy}/{MM}/{32位uuid}.{ext}}，
 * 不含任何用户输入片段（不含原文件名、不含相册名），因此路径穿越在生成阶段就不可能发生；
 * 读取阶段再做一次 {@code normalize + startsWith(root)} 双保险，防御「key 被数据库篡改」的极端情况。</p>
 *
 * <p><b>为什么直接写最终文件名、不用 .tmp 中转</b>：DB 记录在文件写完之后才创建，
 * 进程中途崩溃只会留下无 DB 引用的孤儿文件（无害，可被清理脚本发现）；
 * 而 .tmp 中转会多一次 rename 与清理负担（§8.3）。</p>
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final int UUID_LENGTH = 32;

    private final Path root;

    /**
     * 构造并确保根目录存在。
     *
     * @param properties 存储配置
     */
    public StorageService(StorageProperties properties) {
        this.root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        try {
            // 幂等：目录已存在时不会报错
            Files.createDirectories(this.root);
            log.info("storage root = {}", this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建上传根目录: " + this.root, ex);
        }
    }

    /**
     * 生成存储 key。
     *
     * <p>纯函数、无 IO：便于在事务外先算好 key 再写文件（§8.3 第 1 步）。</p>
     *
     * @param familyId 家庭 id
     * @param date     日期（决定 yyyy/MM 两级目录）
     * @param extension 扩展名（不带点，来自 {@code ImageType}）
     * @return 形如 {@code 1/2026/11/<32hex>.jpg}
     */
    public String allocateKey(Long familyId, LocalDate date, String extension) {
        LocalDate effectiveDate = date == null ? DateTimes.today() : date;
        String uuid = UUID.randomUUID().toString().replace("-", "").toLowerCase();
        if (uuid.length() != UUID_LENGTH) {
            throw new IllegalStateException("UUID 生成异常");
        }
        return familyId + "/" + effectiveDate.getYear() + "/"
                + String.format("%02d", effectiveDate.getMonthValue()) + "/" + uuid + "." + extension;
    }

    /**
     * 由原图 key 推导同目录同名的前缀（用于生成 {@code _thumb} / {@code _preview}）。
     *
     * @param key 形如 {@code 1/2026/11/abc.jpg}
     * @return 形如 {@code 1/2026/11/abc}
     */
    public String keyBase(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    /**
     * 写入字节到指定 key。
     *
     * @param key   存储 key
     * @param bytes 文件内容
     * @return 绝对路径
     */
    public Path writeBytes(String key, byte[] bytes) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return target;
        } catch (IOException ex) {
            log.error("write file failed key={}", key, ex);
            throw new BusinessException(ErrorCode.STORAGE_WRITE_FAILED);
        }
    }

    /**
     * 以流方式写入指定 key（当前实现用字节数组，保留流式入口以便 P1 支持大文件直传）。
     *
     * @param key  存储 key
     * @param input 输入流
     * @return 绝对路径
     */
    public Path write(String key, InputStream input) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            log.error("write stream failed key={}", key, ex);
            throw new BusinessException(ErrorCode.STORAGE_WRITE_FAILED);
        }
    }

    /**
     * 解析 key 为绝对路径（含路径穿越防护）。
     *
     * @param key 存储 key
     * @return 绝对路径
     */
    public Path resolve(String key) {
        String normalizedKey = normalizeKey(key);
        Path target = root.resolve(normalizedKey).normalize();
        if (!target.startsWith(root)) {
            log.warn("path traversal blocked key={}", key);
            throw new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS);
        }
        return target;
    }

    /**
     * 以静态资源形式读取（交给 Spring MVC 处理 Range / Last-Modified）。
     *
     * @param key 存储 key
     * @return 资源
     */
    public Resource loadAsResource(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    /**
     * 判断文件是否存在。
     *
     * @param key 存储 key
     * @return true 表示存在
     */
    public boolean exists(String key) {
        try {
            return Files.isRegularFile(resolve(key));
        } catch (BusinessException ex) {
            return false;
        }
    }

    /**
     * 尽力删除，不抛异常。
     *
     * <p>文件不存在记 DEBUG、其他失败记 WARN：补偿与清理属于「尽力而为」，
     * 绝不能让清理失败覆盖原始业务异常（§6.2 关键设计点）。</p>
     *
     * @param keys 存储 key 集合
     */
    public void deleteQuietly(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            deleteQuietly(key);
        }
    }

    /**
     * 尽力删除单个文件。
     *
     * @param key 存储 key
     */
    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Path target = resolve(key);
            boolean deleted = Files.deleteIfExists(target);
            if (!deleted) {
                log.debug("file not found when deleting key={}", key);
            }
        } catch (BusinessException ex) {
            // key 非法（路径穿越）时不删除，仅记录
            log.warn("skip deleting illegal key={}", key);
        } catch (IOException ex) {
            log.warn("delete file failed key={}", key, ex);
        }
    }

    /**
     * 删除家庭整个存储目录（删家庭 afterCommit 调用）。
     *
     * @param familyId 家庭 id
     */
    public void deleteDirectory(Long familyId) {
        if (familyId == null) {
            return;
        }
        Path directory = resolve(String.valueOf(familyId));
        if (!Files.isDirectory(directory)) {
            log.debug("storage directory not found familyId={}", familyId);
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("delete path failed path={}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("delete directory failed familyId={}", familyId, ex);
        }
    }

    /**
     * 规范化 key：去掉前导 {@code /} 与反斜杠，禁止 {@code ..} 片段。
     *
     * @param key 原始 key
     * @return 规范化 key
     */
    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String cleaned = key.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty() || cleaned.contains("..") || cleaned.contains("\u0000")) {
            throw new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS);
        }
        return cleaned;
    }
}
