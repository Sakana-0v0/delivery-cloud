package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品视图对象
 */
@Data
public class ProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String cover;
    private String description;
    private BigDecimal normPrice;
    private BigDecimal realPrice;
    private Integer stock;
    private Integer sales;
    private Integer status;

    /** 全局点赞数（跨订单、跨用户聚合，来自 t_review） */
    private Integer likeCount;

    /** 全局点踩数（跨订单、跨用户聚合，来自 t_review） */
    private Integer dislikeCount;
}