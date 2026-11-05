package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.dto.photo.BatchDeleteReq;
import com.beefamily.album.dto.photo.BatchMoveReq;
import com.beefamily.album.dto.photo.BatchResultResp;
import com.beefamily.album.dto.photo.PhotoDetailResp;
import com.beefamily.album.dto.photo.PhotoQuery;
import com.beefamily.album.dto.photo.PhotoResp;
import com.beefamily.album.dto.photo.PhotoUpdateReq;
import com.beefamily.album.dto.photo.PhotoUploadResp;
import com.beefamily.album.service.PhotoService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 照片接口：上传 / 浏览 / 编辑 / 删除 / 批量操作。
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class PhotoController {

    private final PhotoService photoService;

    /**
     * 构造控制器。
     *
     * @param photoService 照片服务
     */
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * 照片列表。
     *
     * @param albumId      相册 id
     * @param unclassified 是否只看未分类
     * @param page         页码
     * @param size         每页条数
     * @param sort         排序白名单
     * @return 分页结果
     */
    @GetMapping("/api/photos")
    public ApiResponse<PageResponse<PhotoResp>> list(@RequestParam(required = false) Long albumId,
                                                     @RequestParam(defaultValue = "false") boolean unclassified,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "0") int size,
                                                     @RequestParam(defaultValue = "takenAt_desc") String sort) {
        PhotoQuery query = new PhotoQuery(albumId, unclassified, page, size, sort);
        return ApiResponse.ok(photoService.list(query));
    }

    /**
     * 照片详情。
     *
     * @param id 照片 id
     * @return 详情
     */
    @GetMapping("/api/photos/{id}")
    public ApiResponse<PhotoDetailResp> detail(@PathVariable Long id) {
        return ApiResponse.ok(photoService.detail(id));
    }

    /**
     * 批量上传（前端通常每次 1 张；服务端支持 1-100 张）。
     *
     * @param files   文件列表
     * @param albumId 目标相册 id
     * @return 批量结果
     */
    @PostMapping(value = "/api/photos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhotoUploadResp> upload(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                               @RequestParam(required = false) Long albumId) {
        return ApiResponse.ok(photoService.upload(files, albumId));
    }

    /**
     * 更新照片。
     *
     * <p><b>契约例外</b>：{@code tags} 是完整集合语义（{@code []} 清空、{@code null} 不修改标签），
     * 其余字段遵循全量提交（§11.4）。</p>
     *
     * @param id  照片 id
     * @param req 请求
     * @return 照片响应
     */
    @PatchMapping(value = "/api/photos/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PhotoResp> update(@PathVariable Long id, @Valid @RequestBody PhotoUpdateReq req) {
        return ApiResponse.ok(photoService.update(id, req));
    }

    /**
     * 删除单张照片。
     *
     * @param id 照片 id
     * @return 空响应
     */
    @DeleteMapping("/api/photos/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        photoService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 批量删除。
     *
     * @param req 请求
     * @return 删除条数
     */
    @PostMapping(value = "/api/photos/batch-delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchResultResp> batchDelete(@Valid @RequestBody BatchDeleteReq req) {
        return ApiResponse.ok(photoService.batchDelete(req.photoIds()));
    }

    /**
     * 批量移动。
     *
     * @param req 请求
     * @return 移动条数
     */
    @PostMapping(value = "/api/photos/batch-move", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchResultResp> batchMove(@Valid @RequestBody BatchMoveReq req) {
        return ApiResponse.ok(photoService.batchMove(req));
    }
}
