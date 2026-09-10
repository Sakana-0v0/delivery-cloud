package com.sakana.review.services.impl;

import com.sakana.review.dao.mapper.ReviewMapper;
import com.sakana.review.dto.ReviewCountDTO;

import com.github.benmanes.caffeine.cache.Cache;
import com.sakana.review.services.ReviewCountCacheService;
import com.sakana.review.web.vo.ReviewCountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评价计数缓存服务实现（三级缓存：L1 Caffeine → L2 Redis Hash → L3 MySQL）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCountCacheServiceImpl implements ReviewCountCacheService {

    private static final String REDIS_KEY_PREFIX = "review:count:";
    private static final String FIELD_LIKE = "like";
    private static final String FIELD_BAD = "bad";
    /** 跨实例缓存失效广播频道 */
    private static final String INVALIDATE_CHANNEL = "cache:invalidate:review-count";

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<Long, ReviewCountVO> reviewCountCache;    // L1 Caffeine
    private final Cache<String, String> userVoteCache;            // L1 用户投票
    private final ReviewMapper reviewMapper;

    // ========== 读操作 ==========

    @Override
    public ReviewCountVO getCount(Long productId) {
        if (productId == null) {
            return ReviewCountVO.zero();
        }

        // L1: Caffeine 本地缓存
        ReviewCountVO cached = reviewCountCache.getIfPresent(productId);
        if (cached != null) {
            return cached;
        }

        // L2: Redis Hash
        ReviewCountVO redisResult = getFromRedis(productId);
        if (redisResult != null) {
            // 回填 L1
            reviewCountCache.put(productId, redisResult);
            return redisResult;
        }

        // L3: MySQL 回源
        ReviewCountVO dbResult = getFromDb(productId);
        if (dbResult != null) {
            // 回填 L2
            writeToRedis(productId, dbResult);
            // 回填 L1
            reviewCountCache.put(productId, dbResult);
            return dbResult;
        }

        // 兜底：返回 0
        return ReviewCountVO.zero();
    }

    @Override
    public Map<Long, ReviewCountVO> getCountBatch(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ReviewCountVO> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>(); // 缓存未命中的 ID 列表

        // L1 批量查 Caffeine
        for (Long productId : productIds) {
            ReviewCountVO cached = reviewCountCache.getIfPresent(productId);
            if (cached != null) {
                result.put(productId, cached);
            } else {
                missIds.add(productId);
            }
        }

        if (missIds.isEmpty()) {
            return result;
        }

        // L2 批量查 Redis
        List<Long> stillMissIds = new ArrayList<>();
        for (Long productId : missIds) {
            ReviewCountVO redisResult = getFromRedis(productId);
            if (redisResult != null) {
                result.put(productId, redisResult);
                reviewCountCache.put(productId, redisResult);
            } else {
                stillMissIds.add(productId);
            }
        }

        if (stillMissIds.isEmpty()) {
            return result;
        }

        // L3 批量查 MySQL
        List<ReviewCountDTO> dbResults = reviewMapper.countGroupByProductAndType(stillMissIds);

        // 转换为 Map: productId -> {likeCount, badCount}
        Map<Long, int[]> countMap = new HashMap<>();
        for (ReviewCountDTO dto : dbResults) {
            int[] arr = countMap.computeIfAbsent(dto.getProductId(), k -> new int[2]);
            int value = dto.getCnt() == null ? 0 : dto.getCnt().intValue();
            if (dto.getType() != null && dto.getType() == 1) {
                arr[0] = value; // 点赞
            } else if (dto.getType() != null && dto.getType() == 2) {
                arr[1] = value; // 点踩
            }
        }

        // 回填 L2 和 L1，并加入结果
        for (Long productId : stillMissIds) {
            int[] arr = countMap.get(productId);
            ReviewCountVO vo = arr == null
                    ? ReviewCountVO.zero()
                    : ReviewCountVO.of(arr[0], arr[1]);

            // 回填 Redis
            writeToRedis(productId, vo);
            // 回填 L1
            reviewCountCache.put(productId, vo);
            result.put(productId, vo);
        }

        return result;
    }

    // ========== 写操作 ==========

    /**
     * Lua 脚本：一次 HMSET 原子更新 like/bad 两个字段。
     * - 读取当前值（无值默认 0）
     * - 分别计算增量，保证不为负数
     * - 一次性写入两个字段，不存在中间状态
     */
    private static final String INCR_SCRIPT =
            "local like = redis.call('HGET', KEYS[1], 'like') "
                    + "local bad = redis.call('HGET', KEYS[1], 'bad') "
                    + "like = (like == false or like == nil) and 0 or tonumber(like) "
                    + "bad = (bad == false or bad == nil) and 0 or tonumber(bad) "
                    + "local newLike = math.max(0, like + tonumber(ARGV[1])) "
                    + "local newBad = math.max(0, bad + tonumber(ARGV[2])) "
                    + "redis.call('HMSET', KEYS[1], 'like', newLike, 'bad', newBad) "
                    + "return newLike .. ',' .. newBad";

    @Override
    public void incrementCount(Long productId, int deltaLike, int deltaBad) {
        if (productId == null) {
            log.error("[计数缓存] productId 为 null，跳过增量更新");
            return;
        }

        String redisKey = REDIS_KEY_PREFIX + productId;

        try {
            Object result = stringRedisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(INCR_SCRIPT, String.class),
                    Collections.singletonList(redisKey),
                    String.valueOf(deltaLike),
                    String.valueOf(deltaBad)
            );
            log.info("[计数缓存] Redis Lua 执行结果: key={}, delta=({}, {}), result={}", redisKey, deltaLike, deltaBad, result);
        } catch (Exception e) {
            log.error("[计数缓存] Redis Lua 执行异常: key={}, delta=({}, {}), error={}", redisKey, deltaLike, deltaBad, e.getMessage(), e);
        }
    }

    @Override
    public void invalidateLocal(Long productId) {
        if (productId != null) {
            reviewCountCache.invalidate(productId);
            log.debug("[计数缓存] L1 失效: productId={}", productId);
        }
    }

    @Override
    public void publishInvalidate(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(INVALIDATE_CHANNEL, String.valueOf(productId));
            log.debug("[计数缓存] 广播失效: productId={}", productId);
        } catch (Exception e) {
            log.warn("[计数缓存] 广播失效失败: productId={}, error={}", productId, e.getMessage());
        }
    }

    // ========== 用户投票状态 ==========

    @Override
    public String getUserVote(Long userId, Long orderId, Long productId) {
        String cacheKey = buildUserVoteKey(userId, orderId, productId);
        String cached = userVoteCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        // 缓存未命中，查数据库
        var review = reviewMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sakana.review.dao.entity.Review>()
                        .eq(com.sakana.review.dao.entity.Review::getUserId, userId)
                        .eq(com.sakana.review.dao.entity.Review::getOrderId, orderId)
                        .eq(com.sakana.review.dao.entity.Review::getProductId, productId)
                        .eq(com.sakana.review.dao.entity.Review::getIsDeleted, 0)
        );

        String vote = (review == null) ? "" : (review.getType() == 1 ? "like" : "bad");
        userVoteCache.put(cacheKey, vote);
        return review == null ? null : vote;
    }

    @Override
    public void cacheUserVote(Long userId, Long orderId, Long productId, String vote) {
        String cacheKey = buildUserVoteKey(userId, orderId, productId);
        userVoteCache.put(cacheKey, vote == null ? "" : vote);
    }

    @Override
    public void invalidateUserVote(Long userId, Long orderId, Long productId) {
        String cacheKey = buildUserVoteKey(userId, orderId, productId);
        userVoteCache.invalidate(cacheKey);
    }

    // ========== 私有方法 ==========

    private String buildUserVoteKey(Long userId, Long orderId, Long productId) {
        return userId + ":" + orderId + ":" + productId;
    }

    private ReviewCountVO getFromRedis(Long productId) {
        String redisKey = REDIS_KEY_PREFIX + productId;
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(redisKey);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            int likeCount = parseCount(entries.get(FIELD_LIKE));
            int badCount = parseCount(entries.get(FIELD_BAD));
            return ReviewCountVO.of(likeCount, badCount);
        } catch (Exception e) {
            log.warn("[计数缓存] Redis Hash 读取失败: key={}, error={}", redisKey, e.getMessage());
            return null;
        }
    }

    private void writeToRedis(Long productId, ReviewCountVO vo) {
        String redisKey = REDIS_KEY_PREFIX + productId;
        try {
            Map<String, String> hash = new HashMap<>();
            hash.put(FIELD_LIKE, String.valueOf(vo.getLikeCount()));
            hash.put(FIELD_BAD, String.valueOf(vo.getDislikeCount()));
            stringRedisTemplate.opsForHash().putAll(redisKey, hash);
        } catch (Exception e) {
            log.warn("[计数缓存] Redis Hash 写入失败: key={}, error={}", redisKey, e.getMessage());
        }
    }

    private ReviewCountVO getFromDb(Long productId) {
        List<ReviewCountDTO> list = reviewMapper.countGroupByProductAndType(Collections.singletonList(productId));
        int likeCount = 0;
        int badCount = 0;
        for (ReviewCountDTO dto : list) {
            int value = dto.getCnt() == null ? 0 : dto.getCnt().intValue();
            if (dto.getType() != null && dto.getType() == 1) {
                likeCount = value;
            } else if (dto.getType() != null && dto.getType() == 2) {
                badCount = value;
            }
        }
        return ReviewCountVO.of(likeCount, badCount);
    }

    private int parseCount(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
