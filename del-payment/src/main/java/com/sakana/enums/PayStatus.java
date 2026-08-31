package com.sakana.enums;

/**
 * 支付流水状态（{@code t_payment.status}）。
 *
 * <pre>
 *   0 待支付  1 成功  2 失败  3 关闭
 * </pre>
 */
public enum PayStatus {

    PENDING(0, "待支付"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    CLOSED(3, "关闭");

    private final int code;
    private final String desc;

    PayStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static PayStatus of(int code) {
        for (PayStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知支付状态: " + code);
    }
}
