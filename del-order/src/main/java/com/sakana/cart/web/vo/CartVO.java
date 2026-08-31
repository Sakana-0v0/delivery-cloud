package com.sakana.cart.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车视图对象
 */
@Data
public class CartVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<CartItemVO> items;
    private BigDecimal totalAmount;
    /** 商品种类数（仅可结算商品数量） */
    private Integer itemCount;
}
