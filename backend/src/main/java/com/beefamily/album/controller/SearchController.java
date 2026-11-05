package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.search.SearchResp;
import com.beefamily.album.service.SearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索接口。
 */
@RestController
@RequestMapping(value = "/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {

    private final SearchService searchService;

    /**
     * 构造控制器。
     *
     * @param searchService 搜索服务
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 搜索照片 / 相册 / 里程碑。
     *
     * @param q     关键词（≥1 字符，≤50 字符）
     * @param type  all / photo / album / milestone
     * @param limit 每组条数（默认 20，上限 50）
     * @return 搜索结果
     */
    @GetMapping
    public ApiResponse<SearchResp> search(@RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "all") String type,
                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(searchService.search(q, type, limit));
    }
}
