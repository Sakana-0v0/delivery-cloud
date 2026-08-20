package com.sakana.exceptions;

public interface BaseErrorCode {
    /**
     * 错误码（纯数字，如 3001）
     */
    int getCode();

    /**
     * 错误信息（支持占位符，如 "商品[%s]不存在"）
     */
    String getMessage();

    /**
     * 该错误对应的 HTTP 状态码（404/400/503 等），方便网关处理
     */
    int getHttpStatus();
}