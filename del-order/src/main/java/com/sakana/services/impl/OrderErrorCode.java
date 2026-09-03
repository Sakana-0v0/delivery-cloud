package com.sakana.services.impl;

import com.sakana.exceptions.BaseErrorCode;

/**
 * 订单服务错误码（4xxx 号段）
 */
public enum OrderErrorCode implements BaseErrorCode {

    ORDER_NOT_FOUND(4001, "订单不存在", 404),
    ORDER_NOT_OWN(4002, "无权访问此订单", 403),
    ORDER_STATUS_INVALID(4003, "订单状态不允许此操作", 400),
    ORDER_EMPTY_ITEMS(4004, "订单商品不能为空", 400),
    ORDER_AMOUNT_INVALID(4005, "订单金额异常", 400),
    ORDER_CANCEL_FAIL(4006, "订单取消失败", 400),
    ORDER_CONFIRM_FAIL(4007, "确认收货失败", 400),
    PAYMENT_DEADLINE_PASSED(4008, "支付已超时", 400),
    ORDER_ACCESS_DENIED(4009, "无权访问此订单", 403),
    ORDER_CANNOT_CANCEL(4010, "订单当前状态不允许取消", 400),
    ORDER_CANNOT_CONFIRM(4011, "订单当前状态不允许确认收货", 400),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;

    OrderErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}