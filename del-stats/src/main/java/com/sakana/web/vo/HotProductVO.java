package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 热销商品 VO
 */
@Data
public class HotProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String cover;
    private BigDecimal realPrice;
    private Integer stock;
    private Integer sales;
    private Long categoryId;
    private Integer status;
}
