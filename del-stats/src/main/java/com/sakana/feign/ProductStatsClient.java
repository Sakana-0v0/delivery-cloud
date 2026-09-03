package com.sakana.feign;

import com.sakana.feign.dto.HotProductVO;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品统计服务 Feign 客户端
 * <p>
 * 供 del-stats 聚合服务调用 del-product 的内部统计接口。
 */
@FeignClient(
    name = "del-product",
    contextId = "statsProductStatsClient",
    fallbackFactory = ProductStatsClientFallbackFactory.class
)
public interface ProductStatsClient {

    @GetMapping("/internal/stats/hot-products")
    R<List<HotProductVO>> getHotProducts(@RequestParam(defaultValue = "10") int limit);
}
