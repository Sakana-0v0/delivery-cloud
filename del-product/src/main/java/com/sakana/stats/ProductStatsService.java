package com.sakana.stats;

import com.sakana.stats.dto.HotProductVO;

import java.util.List;

/**
 * 商品统计服务接口（供 del-stats 聚合服务通过内部API调用）
 */
public interface ProductStatsService {

    /**
     * 获取热卖商品列表（按销量倒序）
     *
     * @param limit 返回数量，默认10，最大100
     * @return 热卖商品列表
     */
    List<HotProductVO> getHotProducts(int limit);
}
