package com.sakana.enums;

/**
 * 订单状态
 *
 * <p>包含防御性兜底：UNKNWON(0) 用于数据库脏数据（已存在历史脏状态如 6）
 */
public enum OrderStatus {

    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付"),
    SHIPPING(3, "配送中"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    UNKNOWN(0, "未知状态");

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

    /**
     * 根据码值查找对应枚举，找不到返回 UNKNOWN 兜底（不再抛异常）
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        return UNKNOWN;
    }
}
