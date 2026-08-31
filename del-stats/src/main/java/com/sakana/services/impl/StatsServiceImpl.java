package com.sakana.services.impl;

import com.sakana.feign.OrderStatsClient;
import com.sakana.feign.ProductStatsClient;
import com.sakana.feign.UserStatsClient;
import com.sakana.services.StatsService;
import com.sakana.web.vo.HotProductVO;
import com.sakana.web.vo.SalesTrendResp;
import com.sakana.web.vo.SalesTrendVO;
import com.sakana.web.vo.StatsOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现（Feign 聚合版）
 *
 * <p>通过 Feign 客户端调用各业务服务的内部统计接口，聚合数据返回给 admin。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final OrderStatsClient orderStatsClient;
    private final UserStatsClient userStatsClient;
    private final ProductStatsClient productStatsClient;

    @Override
    public StatsOverviewVO overview() {
        log.info("[StatsService] Fetching stats overview via Feign...");
        
        StatsOverviewVO vo = new StatsOverviewVO();
        
        try {
            // 调用 del-order 获取今日订单统计
            com.sakana.feign.dto.OrderStatsDTO orderStats = orderStatsClient.getTodayStats();
            vo.setTodayOrderCount(orderStats.getTodayOrderCount());
            vo.setTodaySalesAmount(orderStats.getTodaySalesAmount());
        } catch (Exception e) {
            log.error("[StatsService] Failed to fetch order stats: {}", e.getMessage());
            vo.setTodayOrderCount(0L);
            vo.setTodaySalesAmount(BigDecimal.ZERO);
        }
        
        try {
            // 调用 del-user 获取用户统计
            com.sakana.feign.dto.UserStatsDTO userStats = userStatsClient.getUserStats();
            vo.setTotalUserCount(userStats.getTotalUserCount());
            vo.setTodayNewUserCount(userStats.getTodayNewUserCount());
        } catch (Exception e) {
            log.error("[StatsService] Failed to fetch user stats: {}", e.getMessage());
            vo.setTotalUserCount(0L);
            vo.setTodayNewUserCount(0L);
        }
        
        log.info("[StatsService] Overview: todayOrderCount={}, todaySalesAmount={}, totalUserCount={}, todayNewUserCount={}",
                vo.getTodayOrderCount(), vo.getTodaySalesAmount(), vo.getTotalUserCount(), vo.getTodayNewUserCount());
        return vo;
    }

    @Override
    public SalesTrendResp salesTrend(String granularity, LocalDate startDate, LocalDate endDate) {
        log.info("[StatsService] Fetching sales trend via Feign: granularity={}, startDate={}, endDate={}",
                granularity, startDate, endDate);
        
        try {
            // 调用 del-order 获取销售趋势
            com.sakana.feign.dto.SalesTrendDTO trendDTO = orderStatsClient.getSalesTrend(granularity, startDate, endDate);
            
            SalesTrendResp resp = new SalesTrendResp();
            resp.setGranularity(trendDTO.getGranularity());
            
            // 转换数据点
            List<SalesTrendVO> points = trendDTO.getPoints().stream()
                    .map(point -> {
                        SalesTrendVO vo = new SalesTrendVO();
                        vo.setDateKey(point.getDate());
                        vo.setOrderCount(point.getOrderCount());
                        vo.setSalesAmount(point.getSalesAmount());
                        return vo;
                    })
                    .collect(Collectors.toList());
            resp.setPoints(points);
            
            return resp;
        } catch (Exception e) {
            log.error("[StatsService] Failed to fetch sales trend: {}", e.getMessage());
            SalesTrendResp resp = new SalesTrendResp();
            resp.setGranularity(granularity == null ? "day" : granularity);
            resp.setPoints(Collections.emptyList());
            return resp;
        }
    }

    @Override
    public List<HotProductVO> hotProducts(int limit) {
        log.info("[StatsService] Fetching hot products via Feign: limit={}", limit);
        
        try {
            // 调用 del-product 获取热卖商品
            List<com.sakana.feign.dto.HotProductVO> feignProducts = productStatsClient.getHotProducts(limit);
            
            // 转换为 del-stats 的 VO
            return feignProducts.stream()
                    .map(feignVo -> {
                        HotProductVO vo = new HotProductVO();
                        vo.setId(feignVo.getProductId());
                        vo.setName(feignVo.getProductName());
                        vo.setCover(feignVo.getCover());
                        vo.setSales(feignVo.getSales());
                        return vo;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[StatsService] Failed to fetch hot products: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
