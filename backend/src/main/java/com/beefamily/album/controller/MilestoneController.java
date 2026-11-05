package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.dto.milestone.MilestoneReq;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.service.MilestoneService;
import jakarta.validation.Valid;
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
 * 里程碑接口。
 */
@RestController
@RequestMapping(value = "/api/milestones", produces = MediaType.APPLICATION_JSON_VALUE)
public class MilestoneController {

    private final MilestoneService milestoneService;

    /**
     * 构造控制器。
     *
     * @param milestoneService 里程碑服务
     */
    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    /**
     * 里程碑分页。
     *
     * @param babyId 宝宝 id（可选）
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<MilestoneResp>> list(@RequestParam(required = false) Long babyId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "0") int size) {
        return ApiResponse.ok(milestoneService.list(babyId, PageQuery.of(page, size, PageQuery.DEFAULT_LIST_SIZE)));
    }

    /**
     * 新建里程碑。
     *
     * @param req 请求
     * @return 里程碑响应
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MilestoneResp> create(@Valid @RequestBody MilestoneReq req) {
        return ApiResponse.ok(milestoneService.create(req));
    }

    /**
     * 更新里程碑。
     *
     * @param id  里程碑 id
     * @param req 请求
     * @return 里程碑响应
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MilestoneResp> update(@PathVariable Long id, @Valid @RequestBody MilestoneReq req) {
        return ApiResponse.ok(milestoneService.update(id, req));
    }

    /**
     * 删除里程碑。
     *
     * @param id 里程碑 id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        milestoneService.delete(id);
        return ApiResponse.ok();
    }
}
