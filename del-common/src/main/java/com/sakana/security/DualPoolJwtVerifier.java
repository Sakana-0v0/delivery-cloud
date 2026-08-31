package com.sakana.security;

import com.sakana.configs.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 双密钥 JWT 验证器（user-pool + admin-pool）
 *
 * <p>服务（如 del-order / del-product / del-comment / del-user 等同时暴露 C 端与 B 端接口服务）
 * 用本验证器对请求 token 进行一次校验，自动识别其所属池子。
 *
 * <p>顺序：先 user-pool，再 admin-pool。这样保证 C 端 token 永远走 user-pool 路径，避免冲突。
 *
 * <p>本类下沉到 del-common，所有服务可直接 @Autowired 使用。
 */
public class DualPoolJwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(DualPoolJwtVerifier.class);

    public static final String POOL_USER = "USER_POOL";
    public static final String POOL_ADMIN = "ADMIN_POOL";

    private final JwtProperties properties;
    private SecretKey userPoolKey;
    private SecretKey adminPoolKey;
    private boolean adminPoolEnabled = false;

    public DualPoolJwtVerifier(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.userPoolKey = buildKey(properties.getEffectiveUserSecret());
        this.adminPoolKey = buildKey(properties.getAdminPoolSecret());
        this.adminPoolEnabled = properties.getAdminPoolSecret() != null
                && !properties.getAdminPoolSecret().isBlank();
        log.info("[DualPoolJwt] 初始化完成：admin-pool={}",
                adminPoolEnabled ? "启用" : "未配置（仅 C 端）");
    }

    public boolean isAdminPoolEnabled() {
        return adminPoolEnabled;
    }

    /**
     * 验证 token 并返回认证结果
     *
     * @return 验证失败返回 null
     */
    public VerifiedToken verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        // 1. 先 user-pool
        if (tryVerify(token, userPoolKey)) {
            return parseClaims(token, userPoolKey, POOL_USER);
        }
        // 2. 再 admin-pool（仅当配了 admin 密钥）
        if (adminPoolEnabled && tryVerify(token, adminPoolKey)) {
            return parseClaims(token, adminPoolKey, POOL_ADMIN);
        }
        return null;
    }

    private boolean tryVerify(String token, SecretKey key) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private VerifiedToken parseClaims(String token, SecretKey key, String pool) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            String jti = claims.getId();
            return new VerifiedToken(userId, username, role, jti, pool);
        } catch (Exception e) {
            log.warn("[DualPoolJwt] 解析 claims 失败 pool={}: {}", pool, e.getMessage());
            return null;
        }
    }

    private SecretKey buildKey(String secret) {
        String s = secret == null ? "" : secret;
        if (s.length() < 32) {
            s = s + "0".repeat(32 - s.length());
        }
        return Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验证通过后的 token 摘要
     */
    public record VerifiedToken(Long userId, String username, String role, String jti, String pool) {
        public boolean isAdmin() {
            return POOL_ADMIN.equals(pool);
        }
    }
}
