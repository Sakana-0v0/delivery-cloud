package com.sakana.stats.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.stats.UserStatsService;
import com.sakana.stats.dto.UserStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 用户统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsServiceImpl implements UserStatsService {

    private final UserMapper userMapper;

    @Override
    public UserStatsDTO getUserStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 查询累计用户数（未删除的）
        QueryWrapper<User> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("is_deleted", 0);
        Long totalCount = userMapper.selectCount(totalWrapper);

        // 查询今日新增用户数
        QueryWrapper<User> todayWrapper = new QueryWrapper<>();
        todayWrapper.eq("is_deleted", 0)
                .ge("create_time", startOfDay)
                .le("create_time", endOfDay);
        Long todayCount = userMapper.selectCount(todayWrapper);

        UserStatsDTO dto = new UserStatsDTO();
        dto.setTotalUserCount(totalCount);
        dto.setTodayNewUserCount(todayCount);

        log.info("[UserStats] stats: totalCount={}, todayNewCount={}", totalCount, todayCount);
        return dto;
    }
}
