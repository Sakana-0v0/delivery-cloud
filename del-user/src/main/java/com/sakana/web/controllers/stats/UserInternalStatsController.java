package com.sakana.web.controllers.stats;

import com.sakana.stats.UserStatsService;
import com.sakana.stats.dto.UserStatsDTO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户统计内部 API（仅内网可达，供 del-stats 聚合服务调用）
 */
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
@Tag(name = "用户统计-内部", description = "供 del-stats 聚合服务调用的用户统计接口")
public class UserInternalStatsController {

    private final UserStatsService userStatsService;

    @GetMapping("/users")
    @Operation(summary = "获取用户统计数据（供聚合服务调用）")
    public R<UserStatsDTO> getUserStats() {
        return R.ok(userStatsService.getUserStats());
    }
}
