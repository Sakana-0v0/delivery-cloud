package com.sakana.enums;

/**
 * 免单码状态
 */
public enum FreeOrderCouponStatus {

    AVAILABLE("AVAILABLE", "可抢"),
    GRABBED("GRABBED", "已抢"),
    USED("USED", "已使用"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String desc;

    FreeOrderCouponStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }

    public static FreeOrderCouponStatus of(String code) {
        for (FreeOrderCouponStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知免单码状态: " + code);
    }
}