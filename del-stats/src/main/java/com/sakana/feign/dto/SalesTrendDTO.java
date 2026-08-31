package com.sakana.feign.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售趋势数据 DTO（对应 del-order 的 SalesTrendDTO）
 */
@Data
public class SalesTrendDTO {

    /**
     * 粒度：day 或 month
     */
    private String granularity;

    /**
     * 趋势数据点列表
     */
    private List<TrendPoint> points;

    /**
     * 销售趋势数据点
     */
    @Data
    public static class TrendPoint {
        /**
         * 日期（按日查询时格式：yyyy-MM-dd，按月查询时格式：yyyy-MM）
         */
        private String date;

        /**
         * 订单数
         */
        private Long orderCount;

        /**
         * 销售额
         */
        private BigDecimal salesAmount;
    }
}
