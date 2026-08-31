package com.sakana.stats.dto;

import lombok.Data;

/**
 * 用户统计数据 DTO（供 del-stats 聚合服务调用）
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
