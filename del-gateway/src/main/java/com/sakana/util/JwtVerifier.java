package com.sakana.util;

import com.sakana.filter.TokenInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 双密钥验证器（gateway 自带，不依赖 del-common 的 JwtUtil）
 *
 * <p>设计原因：
 * <ul>
 *   <li>C 端服务共享 user-pool-secret</li>
 *   <li>B 端服务独立 admin-pool-secret</li>
 *   <li>网关先按 user-secret 验签，失败再按 admin-secret 验签</li>
 * </ul>
 *
 * <p>安全说明：
 * <ul>
 *   <li>密钥长度必须 >= 32 字节，不足则启动时抛异常（不使用弱填充）</li>
 *   <li>异常分层捕获，便于针对性日志记录和问题诊断</li>
 * </ul>
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    /** JWT 密钥最小长度（256-bit for HS256） */
    private static final int MIN_KEY_LENGTH = 32;

    public static final String POOL_USER = "USER_POOL";
    public static final String POOL_ADMIN = "ADMIN_POOL";

    @Value("${app.jwt.user-pool-secret}")
    private String userPoolSecret;

    @Value("${app.jwt.admin-pool-secret}")
    private String adminPoolSecret;

    private SecretKey userPoolKey;
    private SecretKey adminPoolKey;

    @PostConstruct
    public void init() {
        this.userPoolKey = buildKey(userPoolSecret, "user-pool-secret");
        this.adminPoolKey = buildKey(adminPoolSecret, "admin-pool-secret");
        log.info("[JwtVerifier] 双密钥初始化完成");
    }

    /**
     * 验证 Token 并返回用户信息
     *
     * @param token JWT token 字符串
     * @return TokenInfo 解析成功；null 验签失败
     */
    public TokenInfo verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (tryVerify(token, userPoolKey, "USER_POOL")) {
            return parseClaims(token, userPoolKey, POOL_USER);
        }
        if (tryVerify(token, adminPoolKey, "ADMIN_POOL")) {
            return parseClaims(token, adminPoolKey, POOL_ADMIN);
        }
        return null;
    }

    /**
     * 尝试用指定密钥验签
     *
     * @return true 验签成功；false 验签失败
     */
    private boolean tryVerify(String token, SecretKey key, String poolName) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("[JwtVerifier] {} Token 已过期: {}", poolName, e.getMessage());
        } catch (SignatureException e) {
            log.debug("[JwtVerifier] {} Token 签名不匹配: {}", poolName, e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("[JwtVerifier] {} Token 格式错误: {}", poolName, e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("[JwtVerifier] {} Token 不支持: {}", poolName, e.getMessage());
        } catch (SecurityException e) {
            log.warn("[JwtVerifier] {} Token 安全验证失败: {}", poolName, e.getMessage());
        } catch (JwtException e) {
            log.warn("[JwtVerifier] {} Token 解析异常: {}", poolName, e.getMessage());
        } catch (Exception e) {
            log.error("[JwtVerifier] {} 未知异常: {}", poolName, e.getMessage(), e);
        }
        return false;
    }

    private TokenInfo parseClaims(String token, SecretKey key, String pool) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            return new TokenInfo(userId, username, role, pool);
        } catch (ExpiredJwtException e) {
            log.debug("[JwtVerifier] {} Claims 解析时 Token 已过期: {}", pool, e.getMessage());
        } catch (NumberFormatException e) {
            log.warn("[JwtVerifier] {} Claims 中 userId 格式错误: {}", pool, e.getMessage());
        } catch (Exception e) {
            log.warn("[JwtVerifier] {} 解析 claims 失败: {}", pool, e.getMessage());
        }
        return null;
    }

    /**
     * 构建 HMAC 密钥
     *
     * @param secret    配置密钥
     * @param secretName 密钥名称（用于日志）
     * @return SecretKey
     * @throws IllegalArgumentException 密钥为空或长度不足
     */
    private SecretKey buildKey(String secret, String secretName) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "[JwtVerifier] " + secretName + " 不能为空，请检查配置");
        }
        if (secret.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "[JwtVerifier] " + secretName + " 长度不足 " + MIN_KEY_LENGTH + " 字节，" +
                    "当前 " + secret.length() + " 字节，请使用更长的密钥");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
