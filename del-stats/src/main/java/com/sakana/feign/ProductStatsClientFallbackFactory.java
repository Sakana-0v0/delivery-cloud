package com.sakana.feign;

import com.sakana.feign.dto.HotProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * ProductStatsClient 降级工厂
 */
@Slf4j
@Component
public class ProductStatsClientFallbackFactory implements FallbackFactory<ProductStatsClient> {

    @Override
    public ProductStatsClient create(Throwable cause) {
        log.error("[ProductStatsClient] Feign 调用失败，进入降级逻辑: {}", cause.getMessage());

        return new ProductStatsClient() {
            @Override
            public List<HotProductVO> getHotProducts(int limit) {
                log.warn("[ProductStatsClient] 降级返回：热卖商品为空");
                return Collections.emptyList();
            }
        };
    }
}
