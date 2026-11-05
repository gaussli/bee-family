package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.dto.timeline.TimelinePageResp;
import com.beefamily.album.service.TimelineService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时间轴接口。
 */
@RestController
@RequestMapping(value = "/api/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
public class TimelineController {

    private final TimelineService timelineService;

    /**
     * 构造控制器。
     *
     * @param timelineService 时间轴服务
     */
    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * 时间轴分页（照片 + 里程碑 + 成长记录，按月分组）。
     *
     * @param page 页码（0 基）
     * @param size 每页条数（默认 24）
     * @return 时间轴页
     */
    @GetMapping
    public ApiResponse<TimelinePageResp> page(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "0") int size) {
        return ApiResponse.ok(timelineService.page(
                PageQuery.of(page, size, PageQuery.DEFAULT_TIMELINE_SIZE)));
    }
}
