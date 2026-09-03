package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.VisitLog;
import com.sakana.dao.mapper.VisitLogMapper;
import com.sakana.services.VisitLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 访问统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitLogServiceImpl implements VisitLogService {
    
    private final VisitLogMapper visitLogMapper;
    
    @Override
    @Async
    public void saveLog(VisitLog visitLog) {
        try {
            visitLogMapper.insert(visitLog);
        } catch (Exception e) {
            log.error("保存访问日志失败: {}", visitLog, e);
        }
    }
    
    @Override
    public Long getPv(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        
        LambdaQueryWrapper<VisitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(VisitLog::getCreateTime, start, end);
        
        return visitLogMapper.selectCount(wrapper);
    }
    
    @Override
    public Long getUv(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        
        // 获取去重后的访客ID列表
        LambdaQueryWrapper<VisitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(VisitLog::getCreateTime, start, end)
               .isNotNull(VisitLog::getVisitorId)
               .select(VisitLog::getVisitorId)
               .groupBy(VisitLog::getVisitorId);
        
        List<VisitLog> logs = visitLogMapper.selectList(wrapper);
        return (long) logs.size();
    }
    
    @Override
    public Map<String, Long> getClientStats(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        
        LambdaQueryWrapper<VisitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(VisitLog::getCreateTime, start, end)
               .select(VisitLog::getClientType, VisitLog::getVisitorId);
        
        List<VisitLog> logs = visitLogMapper.selectList(wrapper);
        
        // 按客户端类型分组统计 UV
        Map<String, Set<String>> clientVisitors = new HashMap<>();
        for (VisitLog log : logs) {
            if (log.getVisitorId() != null && log.getClientType() != null) {
                clientVisitors.computeIfAbsent(log.getClientType(), k -> new HashSet<>()).add(log.getVisitorId());
            }
        }
        
        Map<String, Long> result = new LinkedHashMap<>();
        // 保持顺序
        String[] order = {"PC", "Mobile", "Android", "iOS", "Other"};
        for (String type : order) {
            Set<String> visitors = clientVisitors.get(type);
            result.put(type, visitors != null ? (long) visitors.size() : 0L);
        }
        
        return result;
    }
    
    @Override
    public Long getPvRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        
        LambdaQueryWrapper<VisitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(VisitLog::getCreateTime, start, end);
        
        return visitLogMapper.selectCount(wrapper);
    }
    
    @Override
    public Long getUvRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        
        LambdaQueryWrapper<VisitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(VisitLog::getCreateTime, start, end)
               .isNotNull(VisitLog::getVisitorId)
               .select(VisitLog::getVisitorId)
               .groupBy(VisitLog::getVisitorId);
        
        List<VisitLog> logs = visitLogMapper.selectList(wrapper);
        return (long) logs.size();
    }
    
    @Override
    public List<Map<String, Object>> getDailyStats(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Map<String, Object> dayStat = new LinkedHashMap<>();
            dayStat.put("date", current.toString());
            dayStat.put("pv", getPv(current));
            dayStat.put("uv", getUv(current));
            result.add(dayStat);
            current = current.plusDays(1);
        }
        
        return result;
    }
}