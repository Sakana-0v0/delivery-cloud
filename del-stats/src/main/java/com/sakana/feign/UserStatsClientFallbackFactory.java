package com.sakana.feign;

import com.sakana.feign.dto.UserStatsDTO;
import com.sakana.web.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * UserStatsClient fallback factory.
 */
@Slf4j
@Component
public class UserStatsClientFallbackFactory implements FallbackFactory<UserStatsClient> {

    @Override
    public UserStatsClient create(Throwable cause) {
        log.error("[UserStatsClient] Feign call failed: {}", cause.getMessage());

        return new UserStatsClient() {
            @Override
            public R<UserStatsDTO> getUserStats() {
                log.warn("[UserStatsClient] fallback: user stats empty");
                UserStatsDTO dto = new UserStatsDTO();
                dto.setTotalUserCount(0L);
                dto.setTodayNewUserCount(0L);
                return R.ok(dto);
            }
        };
    }
}
