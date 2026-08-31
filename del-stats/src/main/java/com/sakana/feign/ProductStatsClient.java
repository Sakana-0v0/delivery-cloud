package com.sakana.feign;

import com.sakana.feign.dto.HotProductVO;
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
    fallbackFactory = ProductStatsClientFallbackFactory.class
)
public interface ProductStatsClient {

    /**
     * 获取热卖商品列表
     *
     * @param limit 返回数量，默认10，最大100
     * @return 热卖商品列表
     */
    @GetMapping("/internal/stats/hot-products")
    List<HotProductVO> getHotProducts(@RequestParam(defaultValue = "10") int limit);
}
