package com.sakana.feign;

import com.sakana.feign.dto.OrderStatsDTO;
import com.sakana.feign.dto.SalesTrendDTO;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 订单统计服务 Feign 客户端
 * <p>
 * 供 del-stats 聚合服务调用 del-order 的内部统计接口。
 */
@FeignClient(
    name = "del-order",
    contextId = "statsOrderStatsClient",
    fallbackFactory = OrderStatsClientFallbackFactory.class
)
public interface OrderStatsClient {

    @GetMapping("/internal/stats/today")
    R<OrderStatsDTO> getTodayStats();

    @GetMapping("/internal/stats/sales-trend")
    R<SalesTrendDTO> getSalesTrend(
            @RequestParam(required = false, defaultValue = "day") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);
}
