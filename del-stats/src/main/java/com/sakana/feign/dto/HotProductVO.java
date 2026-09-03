package com.sakana.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 热卖商品 VO（对应 del-product 的 HotProductVO）
 */
@Data
public class HotProductVO {

    private Long productId;
    private String productName;
    private String cover;
    private Integer sales;
    private BigDecimal realPrice;
}
