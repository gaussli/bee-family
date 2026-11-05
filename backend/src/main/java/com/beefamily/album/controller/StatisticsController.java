package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.stats.OverviewResp;
import com.beefamily.album.service.StatisticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口。
 */
@RestController
@RequestMapping(value = "/api/stats", produces = MediaType.APPLICATION_JSON_VALUE)
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 构造控制器。
     *
     * @param statisticsService 统计服务
     */
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 概览统计（实时聚合，不缓存）。
     *
     * @return 概览
     */
    @GetMapping("/overview")
    public ApiResponse<OverviewResp> overview() {
        return ApiResponse.ok(statisticsService.overview());
    }
}
