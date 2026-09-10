package com.sakana.search.service;

import com.sakana.search.dto.SearchRequest;
import com.sakana.search.dto.SearchResponse;

/**
 * 搜索服务接口（#SEARCH-001）
 */
public interface SearchService {

    /**
     * 智能搜索
     *
     * @param request 搜索请求
     * @return 搜索结果
     */
    SearchResponse search(SearchRequest request);

    /**
     * 降级搜索（ES 不可用时使用 MySQL LIKE）
     */
    SearchResponse searchFallback(SearchRequest request);
}
