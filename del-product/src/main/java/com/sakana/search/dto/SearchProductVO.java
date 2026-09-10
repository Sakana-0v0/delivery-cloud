package com.sakana.search.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 搜索结果展示 VO（#SEARCH-001-VO）
 *
 * <p>合并 MySQL Product + ES DishDocument，给前端展示用
 */
@Data
@Builder
public class SearchProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 fid（字符串，兼容数字和 UUID） */
    private String id;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 封面图 */
    private String cover;

    /** 分类名（如"汤品"），不是数字 ID */
    private String category;

    /** 实际售价（realPrice 优先，normPrice 兜底） */
    private BigDecimal price;

    /** 热量（来自 ES DishDocument） */
    private Integer calories;

    /** 蛋白质（来自 ES DishDocument） */
    private Float protein;

    /** 脂肪（来自 ES DishDocument） */
    private Float fat;

    /** 是否上架 */
    private Boolean available;
}
