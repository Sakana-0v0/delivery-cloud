package com.sakana.feign;

import com.sakana.feign.dto.UserStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户统计服务 Feign 客户端
 * <p>
 * 供 del-stats 聚合服务调用 del-user 的内部统计接口。
 */
@FeignClient(
    name = "del-user",
    contextId = "statsUserStatsClient",
    fallbackFactory = UserStatsClientFallbackFactory.class
)
public interface UserStatsClient {

    /**
     * 获取用户统计数据
     *
     * @return 累计用户数和今日新增用户数
     */
    @GetMapping("/internal/stats/users")
    UserStatsDTO getUserStats();
}
