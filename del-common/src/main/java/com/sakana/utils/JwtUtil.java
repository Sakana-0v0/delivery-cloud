package com.sakana.utils;

import com.sakana.configs.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 *
 * <p>提供生成、解析、校验 Token 的能力。
 * <p>每个服务可独立配置密钥（app.jwt.secret），生成独立的 token 池，
 * 不同服务之间的 token 不可互相验证（除非 secret 相同）。
 */
@Slf4j
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 确保密钥长度足够（HS256 需要至少 256 位）
        String secret = jwtProperties.getSecret();
        if (secret.length() < 32) {
            secret = secret + "0".repeat(32 - secret.length());
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 AccessToken
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色（USER / ADMIN / SUPER_ADMIN）
     * @return token 字符串
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, jwtProperties.getAccessExpireSeconds() * 1000L);
    }

    /**
     * 生成 RefreshToken
     */
    public String generateRefreshToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, jwtProperties.getRefreshExpireSeconds() * 1000L);
    }

    /**
     * 生成 Token
     */
    private String generateToken(Long userId, String username, String role, long expirationMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("[JWT] Token 已过期: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("[JWT] Token 格式错误: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("[JWT] Token 不支持: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("[JWT] Token 签名失败: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("[JWT] Token 为空或格式错误: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中提取角色
     */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从 Token 中提取 jti
     */
    public String getJti(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    /**
     * 从 Token 中提取过期时间（秒）
     */
    public long getExpirationSeconds(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        return (expiration.getTime() - System.currentTimeMillis()) / 1000;
    }
}
