package com.sakana.services;

import com.sakana.dao.entity.VisitLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 访问统计服务接口
 */
public interface VisitLogService {
    
    /**
     * 保存访问日志
     */
    void saveLog(VisitLog log);
    
    /**
     * 获取指定日期的 PV（页面访问量）
     */
    Long getPv(LocalDate date);
    
    /**
     * 获取指定日期的 UV（独立访客数）
     */
    Long getUv(LocalDate date);
    
    /**
     * 获取指定日期的客户端分布统计
     */
    Map<String, Long> getClientStats(LocalDate date);
    
    /**
     * 获取日期范围内的 PV 总量
     */
    Long getPvRange(LocalDate startDate, LocalDate endDate);
    
    /**
     * 获取日期范围内的 UV 总量
     */
    Long getUvRange(LocalDate startDate, LocalDate endDate);
    
    /**
     * 获取日期范围内的每日统计
     */
    List<Map<String, Object>> getDailyStats(LocalDate startDate, LocalDate endDate);
}