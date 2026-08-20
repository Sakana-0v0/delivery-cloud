package com.sakana.web.vo;

public enum ResultCode {
    SUCCESS(200, "success"),
    NETWORK_ERROR(500, "network error");


    private final Integer code;

    private final String msg;


    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}
