package com.sakana.web.controllers;

import com.sakana.services.VisitLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 访问统计 API（内部服务调用）
 *
 * <p>提供 PV/UV/客户端分布等统计数据
 */
@RestController
@RequestMapping("/internal/stats/visit")
@RequiredArgsConstructor
public class VisitStatsController {
    
    private final VisitLogService visitLogService;
    
    /**
     * 今日统计
     */
    @GetMapping("/today")
    public Map<String, Object> getTodayStats() {
        LocalDate today = LocalDate.now();
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", today.toString());
        result.put("pv", visitLogService.getPv(today));
        result.put("uv", visitLogService.getUv(today));
        result.put("clientStats", visitLogService.getClientStats(today));
        
        return result;
    }
    
    /**
     * 日期范围统计
     */
    @GetMapping("/range")
    public Map<String, Object> getRangeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("pv", visitLogService.getPvRange(startDate, endDate));
        result.put("uv", visitLogService.getUvRange(startDate, endDate));
        result.put("dailyStats", visitLogService.getDailyStats(startDate, endDate));
        
        return result;
    }
}