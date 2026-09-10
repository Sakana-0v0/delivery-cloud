package com.sakana.review.services;


import com.sakana.review.web.vo.ReviewCountVO;

/**
 * 评价计数缓存服务（三级缓存：L1 Caffeine → L2 Redis Hash → L3 MySQL）
 */
public interface ReviewCountCacheService {

    /**
     * 获取商品评价聚合计数
     * 读路径：L2 Redis Hash → L1 Caffeine → L3 MySQL 回源
     *
     * @param productId 商品 ID
     * @return 聚合计数
     */
    ReviewCountVO getCount(Long productId);

    /**
     * 批量获取商品评价聚合计数
     *
     * @param productIds 商品 ID 列表
     * @return productId → ReviewCountVO 映射
     */
    java.util.Map<Long, ReviewCountVO> getCountBatch(java.util.Collection<Long> productIds);

    /**
     * 递增商品评价计数（Redis HINCRBY）
     *
     * @param productId 商品 ID
     * @param deltaLike  点赞增量（可负数）
     * @param deltaBad   点踩增量（可负数）
     */
    void incrementCount(Long productId, int deltaLike, int deltaBad);

    /**
     * 失效 L1 本地缓存
     *
     * @param productId 商品 ID
     */
    void invalidateLocal(Long productId);

    /**
     * 跨实例广播缓存失效（通过 Redis Pub/Sub）
     * @param productId 商品 ID
     */
    void publishInvalidate(Long productId);

    /**
     * 获取用户投票状态（从缓存，未命中查 DB）
     *
     * @param userId    用户 ID
     * @param orderId   订单 ID
     * @param productId 商品 ID
     * @return "like" / "bad" / null
     */
    String getUserVote(Long userId, Long orderId, Long productId);

    /**
     * 缓存用户投票状态
     */
    void cacheUserVote(Long userId, Long orderId, Long productId, String vote);

    /**
     * 失效用户投票状态缓存
     */
    void invalidateUserVote(Long userId, Long orderId, Long productId);
}
