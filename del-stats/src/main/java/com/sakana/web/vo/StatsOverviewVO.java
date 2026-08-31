package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据概览 VO
 */
@Data
public class StatsOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 今日订单数 */
    private Long todayOrderCount;
    /** 今日销售额（已支付+之后） */
    private BigDecimal todaySalesAmount;
    /** 累计用户数 */
    private Long totalUserCount;
    /** 今日新增用户 */
    private Long todayNewUserCount;
}
