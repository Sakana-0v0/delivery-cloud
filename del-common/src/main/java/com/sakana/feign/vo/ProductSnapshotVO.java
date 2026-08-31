package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品快照（del-cart 通过 Feign 调用 del-product 获得的最小可用字段）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只读 del-product 公开 GET /api/v1/products/{id}，避免给 del-product 加内部接口</li>
 *   <li>价格以 realPrice 为准（与 CartService 原逻辑一致）</li>
 *   <li>status=1（下架）或 isDeleted=1 的商品会被 del-product 公开接口拒绝，此处无需再过滤</li>
 * </ul>
 */
@Data
public class ProductSnapshotVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long categoryId;
    private String name;
    private String cover;
    private BigDecimal normPrice;
    private BigDecimal realPrice;
    private Integer stock;
    private Integer sales;
    /** 0=上架，1=下架 */
    private Integer status;
}
