package com.sakana.review.enums;

/**
 * 评价动作枚举（用于 t_review_log 事件日志）
 *
 * <p>不可变枚举，每个值代表一次评价动作的唯一标识
 * <pre>
 * action 值与 t_review_log.action 字段一一对应：
 *   1 = LIKE              新增赞（从无到赞）
 *   2 = BAD               新增踩（从无到踩）
 *   3 = CANCEL_LIKE      取消赞（toggle 或显式取消赞）
 *   4 = CANCEL_BAD       取消踩（toggle 或显式取消踩）
 *   5 = CHANGE_TO_BAD     改投：赞→踩
 *   6 = CHANGE_TO_LIKE    改投：踩→赞
 * </pre>
 */
public enum ReviewAction {

    LIKE(1, "新增赞"),
    BAD(2, "新增踩"),
    CANCEL_LIKE(3, "取消赞"),
    CANCEL_BAD(4, "取消踩"),
    CHANGE_TO_BAD(5, "改投赞→踩"),
    CHANGE_TO_LIKE(6, "改投踩→赞");

    private final int code;
    private final String desc;

    ReviewAction(int code, String desc) {
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
     * 根据 action code 反查枚举
     */
    public static ReviewAction fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReviewAction action : values()) {
            if (action.code == code) {
                return action;
            }
        }
        return null;
    }
}
