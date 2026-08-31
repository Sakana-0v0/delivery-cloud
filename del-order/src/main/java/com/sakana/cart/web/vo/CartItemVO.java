package com.sakana.cart.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项视图对象
 */
@Data
public class CartItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;
    private String cover;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}
