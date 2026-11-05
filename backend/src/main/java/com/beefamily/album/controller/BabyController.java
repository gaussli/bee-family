package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.baby.BabyDetailResp;
import com.beefamily.album.dto.baby.BabyReq;
import com.beefamily.album.dto.baby.BabyResp;
import com.beefamily.album.dto.baby.BabyUpdateReq;
import com.beefamily.album.service.BabyService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 宝宝档案接口。
 */
@RestController
@RequestMapping(value = "/api/babies", produces = MediaType.APPLICATION_JSON_VALUE)
public class BabyController {

    private final BabyService babyService;

    /**
     * 构造控制器。
     *
     * @param babyService 宝宝服务
     */
    public BabyController(BabyService babyService) {
        this.babyService = babyService;
    }

    /**
     * 宝宝列表。
     *
     * @return 列表
     */
    @GetMapping
    public ApiResponse<List<BabyResp>> list() {
        return ApiResponse.ok(babyService.listOfCurrentFamily());
    }

    /**
     * 新建宝宝。
     *
     * @param req 请求
     * @return 宝宝响应
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BabyResp> create(@Valid @RequestBody BabyReq req) {
        return ApiResponse.ok(babyService.create(req));
    }

    /**
     * 宝宝详情。
     *
     * @param id 宝宝 id
     * @return 详情
     */
    @GetMapping("/{id}")
    public ApiResponse<BabyDetailResp> detail(@PathVariable Long id) {
        return ApiResponse.ok(babyService.detail(id));
    }

    /**
     * 更新宝宝档案。
     *
     * @param id  宝宝 id
     * @param req 请求
     * @return 宝宝响应
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BabyResp> update(@PathVariable Long id, @Valid @RequestBody BabyUpdateReq req) {
        return ApiResponse.ok(babyService.update(id, req));
    }

    /**
     * 删除宝宝（仅 owner）。
     *
     * @param id 宝宝 id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        babyService.delete(id);
        return ApiResponse.ok();
    }
}
