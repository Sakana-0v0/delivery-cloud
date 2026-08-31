package com.sakana.feign;

import com.sakana.feign.dto.OrderStatsDTO;
import com.sakana.feign.dto.SalesTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

/**
 * OrderStatsClient 降级工厂
 * <p>
 * 当 del-order 服务不可用时，返回默认的空数据，避免影响整体统计功能。
 */
@Slf4j
@Component
public class OrderStatsClientFallbackFactory implements FallbackFactory<OrderStatsClient> {

    @Override
    public OrderStatsClient create(Throwable cause) {
        log.error("[OrderStatsClient] Feign 调用失败，进入降级逻辑: {}", cause.getMessage());

        return new OrderStatsClient() {
            @Override
            public OrderStatsDTO getTodayStats() {
                log.warn("[OrderStatsClient] 降级返回：今日订单统计为空");
                OrderStatsDTO dto = new OrderStatsDTO();
                dto.setTodayOrderCount(0L);
                dto.setTodaySalesAmount(BigDecimal.ZERO);
                return dto;
            }

            @Override
            public SalesTrendDTO getSalesTrend(String granularity, LocalDate startDate, LocalDate endDate) {
                log.warn("[OrderStatsClient] 降级返回：销售趋势为空");
                SalesTrendDTO dto = new SalesTrendDTO();
                dto.setGranularity(granularity != null ? granularity : "day");
                dto.setPoints(Collections.emptyList());
                return dto;
            }
        };
    }
}
