package com.sakana.stats.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 热卖商品 VO（供 del-stats 聚合服务调用）
 */
@Data
public class HotProductVO {

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品封面图
     */
    private String cover;

    /**
     * 销量
     */
    private Integer sales;
}
