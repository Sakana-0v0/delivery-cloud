package com.sakana.admin.services;

import com.sakana.exceptions.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理员错误码（8xxx 号段）
 */
@Getter
@AllArgsConstructor
public enum AdminErrorCode implements BaseErrorCode {

    LOGIN_FAIL(8001, "用户名或密码错误", 401),
    ADMIN_FROZEN(8002, "账号已被禁用", 403),
    ADMIN_NOT_FOUND(8003, "管理员不存在", 404),
    TOKEN_EXPIRED(8004, "Token已过期", 401),
    REFRESH_TOKEN_INVALID(8005, "RefreshToken无效", 401),
    TOKEN_BLACKLIST(8006, "Token已失效", 401),
    TOKEN_INVALID(8007, "Token无效", 401),
    PARAM_INVALID(1001, "参数校验失败", 400),
    PARAM_MISSING(1002, "缺少必填参数", 400),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;
}
