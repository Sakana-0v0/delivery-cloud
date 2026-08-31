package com.sakana.configs;

import com.sakana.security.DualPoolJwtAuthenticationFilter;
import com.sakana.security.DualPoolJwtVerifier;
import com.sakana.utils.JwtUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * del-common 共享 JWT 自动装配
 *
 * <p>提供：
 * <ul>
 *   <li>{@link JwtUtil}：单密钥 token 生成/解析工具（用于登录服务 del-user / del-admin 生成 token）</li>
 *   <li>{@link DualPoolJwtVerifier}：双密钥 token 验证器（用于网关/其他服务校验 token）</li>
 *   <li>{@link DualPoolJwtAuthenticationFilter}：双密钥认证过滤器（用于资源服务）</li>
 * </ul>
 *
 * <p>由各服务按需 @Autowired 注入。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    /**
     * 单密钥 JwtUtil（保留用于生成 token 和向下兼容）
     */
    @Bean
    @ConditionalOnMissingBean(JwtUtil.class)
    public JwtUtil jwtUtil(JwtProperties jwtProperties) {
        return new JwtUtil(jwtProperties);
    }

    /**
     * 双密钥 JWT 验证器（仅一个实例）
     */
    @Bean
    @ConditionalOnMissingBean(DualPoolJwtVerifier.class)
    public DualPoolJwtVerifier dualPoolJwtVerifier(JwtProperties jwtProperties) {
        return new DualPoolJwtVerifier(jwtProperties);
    }
}
