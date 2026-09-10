package com.sakana.search.web.internal;

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

@Slf4j
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
@Tag(name = "内部搜索API", description = "供内部服务调用的菜品搜索")
@Validated
public class InternalSearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    @Operation(summary = "内部搜索", description = "供 del-cs AI客服 调用的菜品搜索")
    public R<SearchResponse> search(
            @Parameter(description = "搜索关键词")
            @RequestParam @NotBlank String query,

            @Parameter(description = "分类ID")
            @RequestParam(required = false) Long category,

            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1")
            @Min(1) Integer page,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(50) Integer size
    ) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setCategory(category != null ? String.valueOf(category) : null);
        request.setPage(page);
        request.setSize(size);
        return R.ok(searchService.search(request));
    }
}
