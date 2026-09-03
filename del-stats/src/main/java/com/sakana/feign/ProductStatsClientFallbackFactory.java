package com.sakana.feign;

import com.sakana.feign.dto.HotProductVO;
import com.sakana.web.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * ProductStatsClient fallback factory.
 */
@Slf4j
@Component
public class ProductStatsClientFallbackFactory implements FallbackFactory<ProductStatsClient> {

    @Override
    public ProductStatsClient create(Throwable cause) {
        log.error("[ProductStatsClient] Feign call failed: {}", cause.getMessage());

        return new ProductStatsClient() {
            @Override
            public R<List<HotProductVO>> getHotProducts(int limit) {
                log.warn("[ProductStatsClient] fallback: hot products empty");
                return R.ok(Collections.emptyList());
            }
        };
    }
}
