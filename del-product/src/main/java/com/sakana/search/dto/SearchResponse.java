package com.sakana.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索响应 DTO（#SEARCH-001）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /** 搜索结果商品列表 */
    private List<SearchProductVO> products;

    /** ES 命中总数 */
    private Integer total;

    /** 当前页 */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /** 搜索耗时（毫秒） */
    private Integer costMs;
}


