package com.sakana.feign.dto;

import lombok.Data;

/**
 * 热卖商品 VO（对应 del-product 的 HotProductVO）
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
