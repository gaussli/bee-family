package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.dto.growth.GrowthCurveResp;
import com.beefamily.album.dto.growth.GrowthRecordReq;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.service.GrowthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成长记录与成长曲线接口。
 *
 * <p>路径跨两个资源前缀（{@code /api/babies/{id}/...} 与 {@code /api/growth-records/{id}}），
 * 因此不设类级 {@code @RequestMapping} 前缀，逐方法声明完整路径，避免出现需要「拼两次前缀」的歧义。</p>
 */
@RestController
public class GrowthController {

    private final GrowthService growthService;

    /**
     * 构造控制器。
     *
     * @param growthService 成长服务
     */
    public GrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    /**
     * 某宝宝的成长记录分页。
     *
     * @param babyId 宝宝 id
     * @param page   页码（0 基）
     * @param size   每页条数
     * @return 分页结果
     */
    @GetMapping(value = "/api/babies/{babyId}/growth-records", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PageResponse<GrowthRecordResp>> list(@PathVariable Long babyId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "0") int size) {
        return ApiResponse.ok(growthService.list(babyId, PageQuery.of(page, size, PageQuery.DEFAULT_LIST_SIZE)));
    }

    /**
     * 新增 / 覆盖某日期的测量记录（UPSERT）。
     *
     * @param babyId 宝宝 id
     * @param req    请求
     * @return 记录响应
     */
    @PostMapping(value = "/api/babies/{babyId}/growth-records", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GrowthRecordResp> upsert(@PathVariable Long babyId, @Valid @RequestBody GrowthRecordReq req) {
        return ApiResponse.ok(growthService.upsert(babyId, req));
    }

    /**
     * 成长曲线。
     *
     * @param babyId 宝宝 id
     * @param range  区间：3m / 6m / 12m / all
     * @return 曲线
     */
    @GetMapping(value = "/api/babies/{babyId}/growth-curve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GrowthCurveResp> curve(@PathVariable Long babyId,
                                              @RequestParam(defaultValue = "all") String range) {
        return ApiResponse.ok(growthService.curve(babyId, range));
    }

    /**
     * 更新成长记录。
     *
     * @param id  记录 id
     * @param req 请求
     * @return 记录响应
     */
    @PatchMapping(value = "/api/growth-records/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GrowthRecordResp> update(@PathVariable Long id, @Valid @RequestBody GrowthRecordReq req) {
        return ApiResponse.ok(growthService.update(id, req));
    }

    /**
     * 删除成长记录。
     *
     * @param id 记录 id
     * @return 空响应
     */
    @DeleteMapping("/api/growth-records/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        growthService.delete(id);
        return ApiResponse.ok();
    }
}
