package com.sakana.services.impl;

import com.sakana.exceptions.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户服务错误码（2xxx 号段）
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USERNAME_EXISTS(2001, "用户名已存在", 400),
    PHONE_EXISTS(2002, "手机号已被注册", 400),
    EMAIL_EXISTS(2003, "邮箱已被注册", 400),
    LOGIN_FAIL(2004, "用户名或密码错误", 401),
    USER_FROZEN(2005, "账号已被冻结", 403),
    TOKEN_EXPIRED(2006, "Token已过期", 401),
    REFRESH_TOKEN_INVALID(2007, "RefreshToken无效", 401),
    TOKEN_BLACKLIST(2008, "Token已失效", 401),
    TOKEN_INVALID(2009, "Token无效", 401),
    PASSWORD_WEAK(2010, "密码强度不足", 400),
    OLD_PASSWORD_WRONG(2011, "原密码错误", 400),
    PARAM_INVALID(1001, "参数校验失败", 400),
    PARAM_MISSING(1002, "缺少必填参数", 400),
    ADDRESS_NOT_FOUND(4030, "收货地址不存在", 404),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;
}
