package com.sakana.web.controllers.stats;

import com.sakana.stats.OrderStatsService;
import com.sakana.stats.dto.OrderStatsDTO;
import com.sakana.stats.dto.SalesTrendDTO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 订单统计内部 API（仅内网可达，供 del-stats 聚合服务调用）
 */
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
@Tag(name = "订单统计-内部", description = "供 del-stats 聚合服务调用的订单统计接口")
public class OrderInternalStatsController {

    private final OrderStatsService orderStatsService;

    @GetMapping("/today")
    @Operation(summary = "获取今日订单统计数据（供聚合服务调用）")
    public R<OrderStatsDTO> getTodayStats() {
        return R.ok(orderStatsService.getTodayStats());
    }

    @GetMapping("/sales-trend")
    @Operation(summary = "获取销售趋势数据（供聚合服务调用）",
            description = "granularity: day|month，默认 day；不传 start/end 默认 day=近30天，month=近12个月")
    public R<SalesTrendDTO> getSalesTrend(
            @Parameter(description = "粒度：day / month")
            @RequestParam(required = false, defaultValue = "day") String granularity,
            @Parameter(description = "起始日期 yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "截止日期 yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(orderStatsService.getSalesTrend(granularity, startDate, endDate));
    }
}
