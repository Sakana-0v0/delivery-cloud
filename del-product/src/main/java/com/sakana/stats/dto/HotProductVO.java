package com.sakana.stats.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 热卖商品 VO（供 del-stats 聚合服务调用）
 */
@Data
public class HotProductVO {

    private Long productId;
    private String productName;
    private String cover;
    private Integer sales;
    private BigDecimal realPrice;
}
