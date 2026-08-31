package com.sakana.stats.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售趋势数据 DTO（供 del-stats 聚合服务调用）
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
        private String date;
        private Long orderCount;
        private BigDecimal salesAmount;

        public static TrendPoint of(LocalDate date, Long orderCount, BigDecimal salesAmount) {
            TrendPoint point = new TrendPoint();
            point.setDate(date.toString());
            point.setOrderCount(orderCount);
            point.setSalesAmount(salesAmount);
            return point;
        }

        public static TrendPoint ofMonth(String month, Long orderCount, BigDecimal salesAmount) {
            TrendPoint point = new TrendPoint();
            point.setDate(month);
            point.setOrderCount(orderCount);
            point.setSalesAmount(salesAmount);
            return point;
        }
    }
}
