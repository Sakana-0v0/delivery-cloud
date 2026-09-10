package com.sakana.cs.feign;

import com.sakana.search.dto.SearchResponse;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调 del-product 的搜索接口
 */
@FeignClient(name = "del-product", contextId = "csProductFeignClient", path = "/api/v1")
public interface ProductSearchFeignClient {

    @GetMapping("/internal/search")
    R<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    );
}