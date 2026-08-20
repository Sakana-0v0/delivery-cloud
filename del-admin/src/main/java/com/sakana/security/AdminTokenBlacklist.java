package com.sakana.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 管理员 Token 黑名单 + 用户 jti 索引
 * <p>
 * Redis key 前缀独立（del-admin:），与 C 端用户隔离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenBlacklist {

    private static final String KEY_PREFIX = "del-admin:auth:blacklist:";
    private static final String USER_JTI_SET_PREFIX = "del-admin:auth:user:";
    private static final String USER_JTI_SET_SUFFIX = ":jti";
    private static final long USER_JTI_SET_TTL_SECONDS = 14 * 24 * 60 * 60L;

    private final StringRedisTemplate stringRedisTemplate;

    public void add(String jti, long expireSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        try {
            String key = KEY_PREFIX + jti;
            stringRedisTemplate.opsForValue().set(key, "1", expireSeconds, TimeUnit.SECONDS);
            log.info("[AdminTokenBlacklist] jti={} 已加入黑名单，TTL={}s", jti, expireSeconds);
        } catch (Exception e) {
            log.warn("[AdminTokenBlacklist] Redis不可用: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            String key = KEY_PREFIX + jti;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[AdminTokenBlacklist] Redis不可用: {}", e.getMessage());
            return false;
        }
    }

    public void registerUserJti(Long adminId, String jti) {
        if (adminId == null || jti == null || jti.isBlank()) {
            return;
        }
        try {
            String key = userJtiSetKey(adminId);
            stringRedisTemplate.opsForSet().add(key, jti);
            stringRedisTemplate.expire(key, USER_JTI_SET_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AdminTokenBlacklist] Redis不可用: {}", e.getMessage());
        }
    }

    public void unregisterUserJti(Long adminId, String jti) {
        if (adminId == null || jti == null || jti.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.opsForSet().remove(userJtiSetKey(adminId), jti);
        } catch (Exception e) {
            log.warn("[AdminTokenBlacklist] Redis不可用: {}", e.getMessage());
        }
    }

    public void kickOut(Long adminId) {
        if (adminId == null) {
            return;
        }
        try {
            String key = userJtiSetKey(adminId);
            Set<String> jtis = stringRedisTemplate.opsForSet().members(key);
            if (jtis == null || jtis.isEmpty()) {
                return;
            }
            for (String jti : jtis) {
                add(jti, USER_JTI_SET_TTL_SECONDS);
            }
            stringRedisTemplate.delete(key);
            log.warn("[AdminTokenBlacklist.kickOut] adminId={} 已强制下线", adminId);
        } catch (Exception e) {
            log.warn("[AdminTokenBlacklist] Redis不可用: {}", e.getMessage());
        }
    }

    private String userJtiSetKey(Long adminId) {
        return USER_JTI_SET_PREFIX + adminId + USER_JTI_SET_SUFFIX;
    }
}
