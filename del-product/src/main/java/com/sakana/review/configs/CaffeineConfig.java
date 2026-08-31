package com.sakana.review.configs;

import com.sakana.review.web.vo.ReviewCountVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置（L1 缓存）
 */
@Configuration
public class CaffeineConfig {

    /**
     * 商品聚合计数缓存（key = productId）
     * 容量 5000，30 秒 TTL
     */
    @Bean
    public Cache<Long, ReviewCountVO> reviewCountCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    /**
     * 用户投票状态缓存（key = userId:orderId:productId 拼接字符串）
     * 容量 10000，30 秒 TTL
     */
    @Bean
    public Cache<String, String> userVoteCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
}
