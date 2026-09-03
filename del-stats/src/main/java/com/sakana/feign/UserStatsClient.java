package com.sakana.feign;

import com.sakana.feign.dto.UserStatsDTO;
import com.sakana.web.vo.R;
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

    @GetMapping("/internal/stats/users")
    R<UserStatsDTO> getUserStats();
}
