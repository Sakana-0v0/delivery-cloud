package com.sakana.enums;

import com.sakana.exceptions.BaseErrorCode;

/**
 * 支付域错误码。
 * <p>
 * 字面量与单体保持一致（5001/5002/5003...）。新增的跨服务降级码用 6xxx 起步。
 * 免单活动错误码用 9101-9107。
 */
public enum PayErrorCode implements BaseErrorCode {

    PAY_CREATE_FAIL(5001, "支付创建失败", 500),
    PAY_SIGN_INVALID(5002, "支付回调验签失败", 400),
    PAY_AMOUNT_MISMATCH(5003, "支付金额不匹配", 400),
    PAY_ALREADY_PAID(5004, "订单已支付", 409),
    PAY_CLOSED(5005, "订单已关闭", 409),
    PAY_NOT_FOUND(5010, "支付记录不存在", 404),
    PAY_TIMEOUT(5020, "支付超时", 408),

    /** 跨服务调用降级 */
    ORDER_SERVICE_UNAVAILABLE(6001, "订单服务暂时不可用", 503),
    USER_SERVICE_UNAVAILABLE(6002, "用户服务暂时不可用", 503),
    SNAPSHOT_EXPIRED(6010, "订单快照已过期，请重新发起支付", 410),

    // ========== 免单活动错误码 9101-9107 ==========

    /** 活动不存在 */
    FREE_ORDER_ACTIVITY_NOT_FOUND(9101, "活动不存在", 404),

    /** 活动未开始或已结束 */
    FREE_ORDER_NOT_AVAILABLE(9102, "活动未开始或已结束", 400),

    /** 名额已抢完 */
    FREE_ORDER_QUOTA_EXHAUSTED(9103, "免单名额已抢完", 409),

    /** 该用户已抢过本活动 */
    FREE_ORDER_ALREADY_GRABBED(9104, "您已抢过本活动免单", 409),

    /** 免单码无效 */
    FREE_ORDER_CODE_INVALID(9105, "免单码无效", 400),

    /** 免单码不属于当前用户 */
    FREE_ORDER_CODE_NOT_YOURS(9106, "免单码不属于您", 403),

    /** 免单码已使用 */
    FREE_ORDER_CODE_USED(9107, "免单码已使用", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    PayErrorCode(int code, String message, int httpStatus) {
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