package com.sakana.review.configs;

import com.github.benmanes.caffeine.cache.Cache;
import com.sakana.review.web.vo.ReviewCountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 跨实例 Caffeine 缓存失效监听器
 *
 * <p>订阅 Redis Pub/Sub 频道 cache:invalidate:review-count，
 * 收到失效消息后清空本实例的本地 Caffeine 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidateListener implements MessageListener {

    private final Cache<Long, ReviewCountVO> reviewCountCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            Long productId = Long.parseLong(body);
            reviewCountCache.invalidate(productId);
            log.info("[缓存失效] 收到广播，失效 productId={}", productId);
        } catch (NumberFormatException e) {
            log.warn("[缓存失效] 收到非法消息: {}", body);
        }
    }
}
