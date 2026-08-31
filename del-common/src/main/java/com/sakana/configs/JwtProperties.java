package com.sakana.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性
 *
 * <p>绑定 application.yml 中的 app.jwt.* 配置项。
 * 每个服务可独立配置 secret 和过期时间，互不干扰。
 *
 * <p>支持双密钥池（user-pool / admin-pool）：
 * <ul>
 *   <li>仅 C 端服务：只配 user-pool-secret（兼容旧版 secret 字段）</li>
 *   <li>B+C 双端服务：必须同时配 user-pool-secret 和 admin-pool-secret</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * 【已废弃】单密钥模式（兼容老配置），等价于 user-pool-secret
     */
    @Deprecated
    private String secret = "dev-only-secret-change-me-in-production";

    /**
     * C 端 user-pool 密钥。优先使用本字段；为空时回退到 secret。
     */
    private String userPoolSecret;

    /**
     * B 端 admin-pool 密钥。需要支持 admin 接口的服务必须配置。
     */
    private String adminPoolSecret;

    /**
     * AccessToken 有效期（秒），默认 2 小时
     */
    private long accessExpireSeconds = 7200;

    /**
     * RefreshToken 有效期（秒），默认 14 天
     */
    private long refreshExpireSeconds = 1209600;

    /**
     * 解析得到有效 user-pool 密钥（userPoolSecret 优先，否则 secret）
     */
    public String getEffectiveUserSecret() {
        return (userPoolSecret != null && !userPoolSecret.isBlank()) ? userPoolSecret : secret;
    }
}
