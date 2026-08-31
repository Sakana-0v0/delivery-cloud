package com.sakana.feign.dto;

import lombok.Data;

/**
 * 用户统计数据 DTO（对应 del-user 的 UserStatsDTO）
 */
@Data
public class UserStatsDTO {

    /**
     * 累计用户总数
     */
    private Long totalUserCount;

    /**
     * 今日新增用户数
     */
    private Long todayNewUserCount;
}
