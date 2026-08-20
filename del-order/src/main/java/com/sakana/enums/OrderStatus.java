package com.sakana.enums;

/**
 * 订单状态
 */
public enum OrderStatus {

    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付"),
    SHIPPING(3, "配送中"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    ;

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
