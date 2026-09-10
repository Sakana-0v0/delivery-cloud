package com.sakana.search.dto;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;

/**
 * 搜索请求 DTO（#SEARCH-001）
 */
@Data
public class SearchRequest {

    @Parameter(description = "搜索关键词")
    private String query;

    @Parameter(description = "页码")
    private Integer page = 1;

    @Parameter(description = "每页数量")
    private Integer size = 20;

    @Parameter(description = "最高热量上限")
    private Integer maxCalories;

    @Parameter(description = "最高脂肪上限")
    private Float maxFat;

    @Parameter(description = "最低蛋白")
    private Float minProtein;

    @Parameter(description = "分类筛选")
    private String category;
}
