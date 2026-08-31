package com.sakana.feign;

import com.sakana.feign.dto.OrderStatsDTO;
import com.sakana.feign.dto.SalesTrendDTO;
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
    fallbackFactory = OrderStatsClientFallbackFactory.class
)
public interface OrderStatsClient {

    /**
     * 获取今日订单统计数据
     *
     * @return 今日订单数和销售额
     */
    @GetMapping("/internal/stats/today")
    OrderStatsDTO getTodayStats();

    /**
     * 获取销售趋势数据
     *
     * @param granularity 粒度：day 或 month
     * @param startDate   起始日期
     * @param endDate     结束日期
     * @return 销售趋势数据
     */
    @GetMapping("/internal/stats/sales-trend")
    SalesTrendDTO getSalesTrend(
            @RequestParam(required = false, defaultValue = "day") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);
}
