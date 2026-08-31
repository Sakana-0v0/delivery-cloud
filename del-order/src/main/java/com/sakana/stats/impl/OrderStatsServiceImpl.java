package com.sakana.stats.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sakana.dao.entity.Order;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.enums.OrderStatus;
import com.sakana.stats.OrderStatsService;
import com.sakana.stats.dto.OrderStatsDTO;
import com.sakana.stats.dto.SalesTrendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 订单统计服务实现
 * <p>
 * 注意：此服务仅提供统计数据查询，不做复杂的业务逻辑处理。
 * 订单状态流转由 OrderService 负责。
 * <p>
 * 修复说明：
 * - getDailyTrend/getMonthlyTrend 使用数据库层聚合（GROUP BY），避免全量加载到内存
 * - 使用 selectMaps() 获取原始聚合结果，而非实体列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatsServiceImpl implements OrderStatsService {

    private final OrderMapper orderMapper;

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 月份格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public OrderStatsDTO getTodayStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 使用数据库层聚合查询今日已完成订单的统计
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", OrderStatus.COMPLETED.getCode())
                .ge("finish_time", startOfDay)
                .le("finish_time", endOfDay)
                .eq("is_deleted", 0)
                .select("COUNT(*) as todayOrderCount", "SUM(pay_amount) as todaySalesAmount");

        // 使用 selectMaps 获取聚合结果（不映射到实体）
        Map<String, Object> result = orderMapper.selectMaps(queryWrapper).stream().findFirst().orElse(Collections.emptyMap());

        OrderStatsDTO dto = new OrderStatsDTO();
        Object countObj = result.get("todayOrderCount");
        Object amountObj = result.get("todaySalesAmount");

        dto.setTodayOrderCount(countObj != null ? ((Number) countObj).longValue() : 0L);
        dto.setTodaySalesAmount(amountObj != null 
                ? new BigDecimal(amountObj.toString()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        log.info("[OrderStats] Today stats: orderCount={}, salesAmount={}", 
                dto.getTodayOrderCount(), dto.getTodaySalesAmount());
        return dto;
    }

    @Override
    public SalesTrendDTO getSalesTrend(String granularity, LocalDate startDate, LocalDate endDate) {
        SalesTrendDTO dto = new SalesTrendDTO();
        String gran = (granularity == null || (!granularity.equals("day") && !granularity.equals("month")))
                ? "day" : granularity;
        dto.setGranularity(gran);

        if ("month".equals(gran)) {
            // 按月聚合
            if (startDate == null) {
                startDate = LocalDate.now().minusMonths(11).withDayOfMonth(1);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            List<SalesTrendDTO.TrendPoint> points = getMonthlyTrend(startDate, endDate);
            dto.setPoints(points);
        } else {
            // 按日聚合
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(29);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            List<SalesTrendDTO.TrendPoint> points = getDailyTrend(startDate, endDate);
            dto.setPoints(points);
        }

        return dto;
    }

    /**
     * 按日聚合销售趋势（使用数据库层 GROUP BY 聚合）
     */
    private List<SalesTrendDTO.TrendPoint> getDailyTrend(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 使用数据库层 GROUP BY 聚合，避免全量数据加载到内存
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", OrderStatus.COMPLETED.getCode())
                .ge("finish_time", startDateTime)
                .le("finish_time", endDateTime)
                .eq("is_deleted", 0)
                .select("DATE(finish_time) as dateStr", 
                        "COUNT(*) as orderCount", 
                        "SUM(pay_amount) as totalAmount")
                .groupBy("DATE(finish_time)")
                .orderByAsc("DATE(finish_time)");

        // 使用 selectMaps 获取聚合结果
        List<Map<String, Object>> dbResults = orderMapper.selectMaps(queryWrapper);

        // 转换为 map，便于快速查找
        java.util.function.Function<Map<String, Object>, String> dateExtractor = m -> {
            Object dateObj = m.get("dateStr");
            if (dateObj instanceof java.sql.Date) {
                return ((java.sql.Date) dateObj).toLocalDate().format(DATE_FORMATTER);
            } else if (dateObj instanceof LocalDateTime) {
                return ((LocalDateTime) dateObj).toLocalDate().format(DATE_FORMATTER);
            } else if (dateObj instanceof LocalDate) {
                return ((LocalDate) dateObj).format(DATE_FORMATTER);
            }
            return dateObj != null ? dateObj.toString() : "";
        };

        final java.util.function.Function<Map<String, Object>, String> finalDateExtractor = dateExtractor;
        final Map<String, Map<String, Object>> resultMap = dbResults.stream()
                .collect(java.util.stream.Collectors.toMap(finalDateExtractor, m -> m));

        // 生成日期范围内的所有日期点（补充零值）
        List<SalesTrendDTO.TrendPoint> points = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String dateStr = current.format(DATE_FORMATTER);
            Map<String, Object> dayData = resultMap.get(dateStr);
            
            long count = 0L;
            BigDecimal amount = BigDecimal.ZERO;
            if (dayData != null) {
                Object countObj = dayData.get("orderCount");
                Object amountObj = dayData.get("totalAmount");
                count = countObj != null ? ((Number) countObj).longValue() : 0L;
                amount = amountObj != null 
                        ? new BigDecimal(amountObj.toString()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
            }
            
            points.add(SalesTrendDTO.TrendPoint.of(current, count, amount));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * 按月聚合销售趋势（使用数据库层 GROUP BY 聚合）
     */
    private List<SalesTrendDTO.TrendPoint> getMonthlyTrend(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endDateTime = endDate.withDayOfMonth(endDate.lengthOfMonth()).atTime(LocalTime.MAX);

        // 使用数据库层 GROUP BY 聚合
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", OrderStatus.COMPLETED.getCode())
                .ge("finish_time", startDateTime)
                .le("finish_time", endDateTime)
                .eq("is_deleted", 0)
                .select("DATE_FORMAT(finish_time, '%Y-%m') as monthStr", 
                        "COUNT(*) as orderCount", 
                        "SUM(pay_amount) as totalAmount")
                .groupBy("DATE_FORMAT(finish_time, '%Y-%m')")
                .orderByAsc("DATE_FORMAT(finish_time, '%Y-%m')");

        List<Map<String, Object>> dbResults = orderMapper.selectMaps(queryWrapper);

        // 转换为 map
        final Map<String, Map<String, Object>> resultMap = dbResults.stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> m.get("monthStr").toString(), 
                        m -> m));

        // 生成月份范围内的所有月份点（补充零值）
        List<SalesTrendDTO.TrendPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        while (!current.isAfter(end)) {
            String monthStr = current.format(MONTH_FORMATTER);
            Map<String, Object> monthData = resultMap.get(monthStr);
            
            long count = 0L;
            BigDecimal amount = BigDecimal.ZERO;
            if (monthData != null) {
                Object countObj = monthData.get("orderCount");
                Object amountObj = monthData.get("totalAmount");
                count = countObj != null ? ((Number) countObj).longValue() : 0L;
                amount = amountObj != null 
                        ? new BigDecimal(amountObj.toString()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
            }
            
            points.add(SalesTrendDTO.TrendPoint.ofMonth(monthStr, count, amount));
            current = current.plusMonths(1);
        }

        return points;
    }
}
