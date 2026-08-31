package com.sakana.cart.enums;

import com.sakana.exceptions.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 购物车服务错误码（4xxx 号段，购物车专属部分）
 */
@Getter
@AllArgsConstructor
public enum CartErrorCode implements BaseErrorCode {

    CART_EMPTY(4001, "购物车为空", 400),
    CART_ITEM_NOT_FOUND(4002, "购物车项不存在", 404),
    CART_ITEM_INVALID(4003, "购物车参数非法", 400),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;
}
