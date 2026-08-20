package com.sakana.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 订单服务的 Token 黑名单
 *
 * <p>del-order 主要做 token 校验，黑名单写入主要在 del-user 登出时发生，
 * 这里的 isBlacklisted 用于拦截已经登出的 token。
 * <p>
 * 实际黑名单与 del-user 共享 Redis key 前缀（del-user:auth:blacklist:*），
 * 因为 C 端共用一个 token 池。
 */
@Component
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);
    private static final String KEY_PREFIX = "del-user:auth:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenBlacklist(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            String key = KEY_PREFIX + jti;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[OrderTokenBlacklist] Redis不可用: {}", e.getMessage());
            return false;
        }
    }
}
