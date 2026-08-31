package com.sakana.stats.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单统计数据 DTO（供 del-stats 聚合服务调用）
 */
@Data
public class OrderStatsDTO {

    /**
     * 今日已完成订单数
     */
    private Long todayOrderCount;

    /**
     * 今日销售额（已完成订单的实付金额总和）
     */
    private BigDecimal todaySalesAmount;
}
