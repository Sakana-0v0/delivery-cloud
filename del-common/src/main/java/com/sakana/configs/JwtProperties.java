package com.sakana.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性
 *
 * <p>绑定 application.yml 中的 app.jwt.* 配置项。
 * 每个服务可独立配置 secret 和过期时间，互不干扰。
 */
@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * JWT 密钥（每个服务独立配置）
     */
    private String secret = "dev-only-secret-change-me-in-production";

    /**
     * AccessToken 有效期（秒），默认 2 小时
     */
    private long accessExpireSeconds = 7200;

    /**
     * RefreshToken 有效期（秒），默认 14 天
     */
    private long refreshExpireSeconds = 1209600;
}
