package com.sakana.feign;

import com.sakana.feign.dto.OrderStatsDTO;
import com.sakana.feign.dto.SalesTrendDTO;
import com.sakana.web.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

/**
 * OrderStatsClient fallback factory.
 */
@Slf4j
@Component
public class OrderStatsClientFallbackFactory implements FallbackFactory<OrderStatsClient> {

    @Override
    public OrderStatsClient create(Throwable cause) {
        log.error("[OrderStatsClient] Feign call failed: {}", cause.getMessage());

        return new OrderStatsClient() {
            @Override
            public R<OrderStatsDTO> getTodayStats() {
                log.warn("[OrderStatsClient] fallback: today order stats empty");
                OrderStatsDTO dto = new OrderStatsDTO();
                dto.setTodayOrderCount(0L);
                dto.setTodaySalesAmount(BigDecimal.ZERO);
                return R.ok(dto);
            }

            @Override
            public R<SalesTrendDTO> getSalesTrend(String granularity, LocalDate startDate, LocalDate endDate) {
                log.warn("[OrderStatsClient] fallback: sales trend empty");
                SalesTrendDTO dto = new SalesTrendDTO();
                dto.setGranularity(granularity != null ? granularity : "day");
                dto.setPoints(Collections.emptyList());
                return R.ok(dto);
            }
        };
    }
}
