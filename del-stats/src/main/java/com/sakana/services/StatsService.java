package com.sakana.services;

import com.sakana.web.vo.HotProductVO;
import com.sakana.web.vo.SalesTrendResp;
import com.sakana.web.vo.StatsOverviewVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据统计服务接口
 */
public interface StatsService {

    /**
     * 数据概览：今日订单数、销售额、用户数
     */
    StatsOverviewVO overview();

    /**
     * 销售趋势（按日 / 月聚合）
     */
    SalesTrendResp salesTrend(String granularity, LocalDate startDate, LocalDate endDate);

    /**
     * 热门商品 Top N
     */
    List<HotProductVO> hotProducts(int limit);
}
