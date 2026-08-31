package com.sakana.review.enums;

import com.sakana.exceptions.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评价服务错误码
 */
@Getter
@AllArgsConstructor
public enum ReviewErrorCode implements BaseErrorCode {

    REVIEW_SWITCH_PARAM_INVALID(4050, "开关参数必须为 0 或 1", 400),
    REVIEW_SWITCH_CLOSED(4051, "评价功能已关闭", 400),
    REVIEW_VOTE_PARAM_INVALID(4052, "评价类型必须为 like / bad / null", 400),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;
}