package com.sakana.stats;

import com.sakana.stats.dto.OrderStatsDTO;
import com.sakana.stats.dto.SalesTrendDTO;

import java.time.LocalDate;

/**
 * 订单统计服务接口（供 del-stats 聚合服务通过内部API调用）
 */
public interface OrderStatsService {

    /**
     * 获取今日订单统计数据
     *
     * @return 今日订单数和销售额
     */
    OrderStatsDTO getTodayStats();

    /**
     * 获取销售趋势数据
     *
     * @param granularity 粒度：day 或 month
     * @param startDate   起始日期（可为空，使用默认值）
     * @param endDate     结束日期（可为空，使用默认值）
     * @return 销售趋势数据
     */
    SalesTrendDTO getSalesTrend(String granularity, LocalDate startDate, LocalDate endDate);
}
