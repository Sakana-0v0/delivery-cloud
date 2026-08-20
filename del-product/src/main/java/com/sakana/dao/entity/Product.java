package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product")
public class Product extends BaseEntity {
    /**
     * 商品ID
    **/

    private String fid;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 封面图片URL
     */
    private String cover;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 原价
     */
    private BigDecimal normPrice;

    /**
     * 现价（实际售价）
     */
    private BigDecimal realPrice;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 销量
     */
    private Integer sales;

    /**
     * 状态（0=上架，1=下架）
     */
    private Integer status;
}