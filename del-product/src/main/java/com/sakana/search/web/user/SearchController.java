package com.sakana.search.web.user;

import com.sakana.search.dto.SearchRequest;
import com.sakana.search.dto.SearchResponse;
import com.sakana.search.service.SearchService;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * C 端菜品搜索 API（#SEARCH-001）
 *
 * <p>路由：GET /api/v1/search
 * <p>支持：分词匹配 + 语义扩展 + 分类筛选
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "C端菜品搜索", description = "支持分词匹配和语义扩展的菜品搜索")
@Validated
public class SearchController {

    private final SearchService searchService;

    /**
     * 搜索菜品
     *
     * @param query    搜索关键词（如"清淡"、"番茄"）
     * @param category 分类ID（可选）
     * @param page     页码（默认 1）
     * @param size     每页条数（默认 20）
     */
    @GetMapping
    @Operation(summary = "搜索菜品", description = "基于 Ansj Seg 分词 + 语义扩展的菜品搜索")
    public R<SearchResponse> search(
            @Parameter(description = "搜索关键词")
            @RequestParam @NotBlank String query,

            @Parameter(description = "分类ID")
            @RequestParam(required = false) Long category,

            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1")
            @Min(1) Integer page,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(50) Integer size
    ) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        // category 是 Long，SearchRequest.category 是 String
        request.setCategory(category != null ? String.valueOf(category) : null);
        request.setPage(page);
        request.setSize(size);
        return R.ok(searchService.search(request));
    }

}