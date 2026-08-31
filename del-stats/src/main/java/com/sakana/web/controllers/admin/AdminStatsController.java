package com.sakana.web.controllers.admin;

import com.sakana.services.StatsService;
import com.sakana.web.vo.HotProductVO;
import com.sakana.web.vo.R;
import com.sakana.web.vo.SalesTrendResp;
import com.sakana.web.vo.StatsOverviewVO;
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
import java.util.List;

/**
 * 管理后台 - 数据统计（独立聚合服务 del-stats）
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "管理后台-统计", description = "概览、销售趋势、热卖商品")
public class AdminStatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    @Operation(summary = "数据概览：今日订单数/销售额/累计用户/今日新用户")
    public R<StatsOverviewVO> overview() {
        return R.ok(statsService.overview());
    }

    @GetMapping("/sales")
    @Operation(summary = "销售趋势（按日/按月聚合）",
            description = "granularity: day|month，默认 day；不传 start/end 默认 day=近30天，month=近12个月")
    public R<SalesTrendResp> sales(
            @Parameter(description = "粒度：day / month")
            @RequestParam(required = false, defaultValue = "day") String granularity,
            @Parameter(description = "起始日期 yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "截止日期 yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statsService.salesTrend(granularity, startDate, endDate));
    }

    @GetMapping("/hot-products")
    @Operation(summary = "热门商品 Top N（按销量倒序，含全部上下架）")
    public R<List<HotProductVO>> hotProducts(
            @Parameter(description = "返回数量，默认10，最大100")
            @RequestParam(defaultValue = "10") int limit) {
        return R.ok(statsService.hotProducts(limit));
    }
}
