package com.sakana.feign;

import com.sakana.feign.dto.UserStatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * UserStatsClient 降级工厂
 */
@Slf4j
@Component
public class UserStatsClientFallbackFactory implements FallbackFactory<UserStatsClient> {

    @Override
    public UserStatsClient create(Throwable cause) {
        log.error("[UserStatsClient] Feign 调用失败，进入降级逻辑: {}", cause.getMessage());

        return new UserStatsClient() {
            @Override
            public UserStatsDTO getUserStats() {
                log.warn("[UserStatsClient] 降级返回：用户统计为空");
                UserStatsDTO dto = new UserStatsDTO();
                dto.setTotalUserCount(0L);
                dto.setTodayNewUserCount(0L);
                return dto;
            }
        };
    }
}
