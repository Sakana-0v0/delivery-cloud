package com.sakana.stats;

import com.sakana.stats.dto.UserStatsDTO;

/**
 * 用户统计服务接口（供 del-stats 聚合服务通过内部API调用）
 */
public interface UserStatsService {

    /**
     * 获取用户统计数据
     *
     * @return 累计用户数和今日新增用户数
     */
    UserStatsDTO getUserStats();
}
