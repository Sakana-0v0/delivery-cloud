package com.sakana.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单 + 用户 jti 索引
 *
 * <ul>
 *   <li>黑名单：单条 jti 失效（登出、密码修改）</li>
 *   <li>用户 jti 索引（Redis SET）：记录该用户的所有活跃 token jti，用于一键踢下线（冻结用户）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String KEY_PREFIX = "del-user:auth:blacklist:";
    private static final String USER_JTI_SET_PREFIX = "del-user:auth:user:";
    private static final String USER_JTI_SET_SUFFIX = ":jti";
    /** 用户 jti 索引 SET 的 TTL：覆盖 refreshToken 有效期（14 天），过期自动清理 */
    private static final long USER_JTI_SET_TTL_SECONDS = 14 * 24 * 60 * 60L;

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 单条 jti 黑名单 ====================

    /**
     * 将 token jti 加入黑名单
     */
    public void add(String jti, long expireSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        try {
            String key = KEY_PREFIX + jti;
            stringRedisTemplate.opsForValue().set(key, "1", expireSeconds, TimeUnit.SECONDS);
            log.info("[TokenBlacklist] jti={} 已加入黑名单，TTL={}s", jti, expireSeconds);
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis不可用，跳过黑名单登记: {}", e.getMessage());
        }
    }

    /**
     * 检查 token jti 是否在黑名单中
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            String key = KEY_PREFIX + jti;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis不可用，默认不黑名单: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 用户 jti 索引（用于一键踢下线） ====================

    /**
     * 登记用户登录的 token jti（登录时调用）
     */
    public void registerUserJti(Long userId, String jti) {
        if (userId == null || jti == null || jti.isBlank()) {
            return;
        }
        try {
            String key = userJtiSetKey(userId);
            stringRedisTemplate.opsForSet().add(key, jti);
            stringRedisTemplate.expire(key, USER_JTI_SET_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis不可用，跳过用户jti登记: {}", e.getMessage());
        }
    }

    /**
     * 注销用户登出的 token jti（登出时调用）
     */
    public void unregisterUserJti(Long userId, String jti) {
        if (userId == null || jti == null || jti.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().remove(userJtiSetKey(userId), jti);
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis不可用，跳过用户jti注销: {}", e.getMessage());
        }
    }

    /**
     * 踢下线（冻结用户时调用）
     */
    public void kickOut(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = userJtiSetKey(userId);
            Set<String> jtis = stringRedisTemplate.opsForSet().members(key);
            if (jtis == null || jtis.isEmpty()) {
                log.info("[TokenBlacklist.kickOut] userId={} 无活跃 token", userId);
                return;
            }
            for (String jti : jtis) {
                add(jti, USER_JTI_SET_TTL_SECONDS);
            }
            stringRedisTemplate.delete(key);
            log.warn("[TokenBlacklist.kickOut] userId={} 已强制下线，影响 {} 个 token",
                    userId, jtis.size());
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis不可用，跳过踢下线: {}", e.getMessage());
        }
    }

    private String userJtiSetKey(Long userId) {
        return USER_JTI_SET_PREFIX + userId + USER_JTI_SET_SUFFIX;
    }
}
