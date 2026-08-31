package com.sakana.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单统计数据 DTO（对应 del-order 的 OrderStatsDTO）
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
