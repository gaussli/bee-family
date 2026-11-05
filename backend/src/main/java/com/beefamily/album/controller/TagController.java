package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.search.TagResp;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.service.TagService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签接口（输入即搜索 + 最近使用）。
 */
@RestController
@RequestMapping(value = "/api/tags", produces = MediaType.APPLICATION_JSON_VALUE)
public class TagController {

    private final TagService tagService;
    private final FamilyAccessGuard guard;

    /**
     * 构造控制器。
     *
     * @param tagService 标签服务
     * @param guard      家庭闸门（取 familyId）
     */
    public TagController(TagService tagService, FamilyAccessGuard guard) {
        this.tagService = tagService;
        this.guard = guard;
    }

    /**
     * 标签列表。
     *
     * @param q 前缀关键字，可为空（空则按最近使用倒序）
     * @return 标签列表
     */
    @GetMapping
    public ApiResponse<List<TagResp>> list(@RequestParam(required = false) String q) {
        return ApiResponse.ok(tagService.list(guard.familyId(), q));
    }
}
