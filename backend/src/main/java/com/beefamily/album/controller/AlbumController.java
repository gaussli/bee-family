package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.album.AlbumDetailResp;
import com.beefamily.album.dto.album.AlbumListResp;
import com.beefamily.album.dto.album.AlbumReq;
import com.beefamily.album.dto.album.AlbumResp;
import com.beefamily.album.dto.album.AlbumUpdateReq;
import com.beefamily.album.dto.album.DeleteAlbumResp;
import com.beefamily.album.dto.album.PhotoStrategy;
import com.beefamily.album.service.AlbumService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 相册接口。
 */
@RestController
@RequestMapping(value = "/api/albums", produces = MediaType.APPLICATION_JSON_VALUE)
public class AlbumController {

    private static final Logger log = LoggerFactory.getLogger(AlbumController.class);

    private final AlbumService albumService;

    /**
     * 构造控制器。
     *
     * @param albumService 相册服务
     */
    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /**
     * 相册列表 + 未分类汇总。
     *
     * @return 列表
     */
    @GetMapping
    public ApiResponse<AlbumListResp> list() {
        return ApiResponse.ok(albumService.list());
    }

    /**
     * 新建相册。
     *
     * @param req 请求
     * @return 相册响应
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AlbumResp> create(@Valid @RequestBody AlbumReq req) {
        return ApiResponse.ok(albumService.create(req));
    }

    /**
     * 相册详情。
     *
     * @param id 相册 id
     * @return 详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AlbumDetailResp> detail(@PathVariable Long id) {
        return ApiResponse.ok(albumService.detail(id));
    }

    /**
     * 更新相册（创建者或 owner）。
     *
     * <p><b>为什么这里要重试一次</b>：设封面（本接口）与删照片
     * （{@code PhotoService.deletePhotosBulk}）并发时，InnoDB 偶尔会因「相册行排他锁 ↔ 照片行
     * 外键共享锁」互等而回滚其中一个事务，抛
     * {@code PessimisticLockingFailureException}（含 {@code DeadlockLoserDataAccessException} /
     * {@code CannotAcquireLockException}）。这类异常是**可重试**的，直接冒泡会变成用户的 500。
     * {@code AlbumService#update} 自带事务边界，所以在这里再调一次就是重开一个干净事务。
     * 重试仍失败才向上抛（由全局异常处理映射成 50001）。</p>
     *
     * @param id  相册 id
     * @param req 请求
     * @return 相册响应
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AlbumResp> update(@PathVariable Long id, @Valid @RequestBody AlbumUpdateReq req) {
        try {
            return ApiResponse.ok(albumService.update(id, req));
        } catch (PessimisticLockingFailureException ex) {
            log.warn("album update lock conflict, retrying once albumId={} cause={}", id,
                    ex.getClass().getSimpleName());
            return ApiResponse.ok(albumService.update(id, req));
        }
    }

    /**
     * 删除相册（创建者或 owner）。
     *
     * <p>{@code photoStrategy} 由 Spring 直接绑定枚举，非法取值会抛
     * {@code MethodArgumentTypeMismatchException} → 40002（见 §3.4 异常矩阵）。</p>
     *
     * @param id            相册 id
     * @param photoStrategy 照片处置策略，默认 move
     * @return 处置结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteAlbumResp> delete(@PathVariable Long id,
                                               @RequestParam(defaultValue = "move") PhotoStrategy photoStrategy) {
        return ApiResponse.ok(albumService.delete(id, photoStrategy));
    }
}
