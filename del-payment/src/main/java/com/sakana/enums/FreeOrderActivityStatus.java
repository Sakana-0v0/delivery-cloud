package com.sakana.enums;

/**
 * 抢免单活动状态
 */
public enum FreeOrderActivityStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    GRABBING("GRABBING", "抢购中"),
    CLOSED("CLOSED", "已结束");

    private final String code;
    private final String desc;

    FreeOrderActivityStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }

    public static FreeOrderActivityStatus of(String code) {
        for (FreeOrderActivityStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知活动状态: " + code);
    }
}