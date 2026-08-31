package com.sakana.enums;

/**
 * user_event_outbox 行状态机。
 *
 * <pre>
 *   0 NEW  →  1 SENT  →  2 ACK  （成功路径）
 *                     ↘  3 DLQ  （重试耗尽，进入死信）
 * </pre>
 */
public enum UserOutboxStatus {

    NEW(0, "新写入"),
    SENT(1, "已投递MQ"),
    ACK(2, "消费方已确认"),
    DLQ(3, "进入死信");

    private final int code;
    private final String desc;

    UserOutboxStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static UserOutboxStatus of(int code) {
        for (UserOutboxStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知 outbox 状态: " + code);
    }
}
