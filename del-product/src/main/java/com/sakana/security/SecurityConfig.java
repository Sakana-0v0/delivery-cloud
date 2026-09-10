package com.sakana.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * del-product Spring Security 配置（双密钥：user-pool + admin-pool）
 *
 * <p>路径规则：
 * <ul>
 *   <li>/api/v1/products/** + /api/v1/categories/** → 公开（C 端商品浏览）</li>
 *   <li>/api/v1/admin/products/** + /api/v1/admin/categories/** → ADMIN / SUPER_ADMIN</li>
 *   <li>/internal/** → 内部服务调用（需 X-Internal-Service-Token 头）</li>
 * </ul>
 * <p>CORS 配置统一由网关处理，业务服务不参与。
 */
@Configuration("productSecurityConfig")
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DualPoolJwtAuthenticationFilter dualPoolJwtAuthenticationFilter;
    private final InternalServiceAuthFilter internalServiceAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公开：商品浏览 / 分类
                        .requestMatchers("/api/v1/products/**", "/api/v1/categories/**").permitAll()
                        // C 端：评论列表 + 投票（合并 del-comment）
                        .requestMatchers("/api/v1/products/*/reviews").authenticated()
                        .requestMatchers("/api/v1/reviews/*/vote").authenticated()
                        // 内部接口（需内部服务认证）
                        .requestMatchers("/internal/**", "/api/v1/internal/**").hasRole("INTERNAL_SERVICE")
                        // Swagger / Actuator
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // B 端管理
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                // 内部服务认证过滤器（最先执行）
                .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 双池 JWT 认证过滤器
                .addFilterBefore(dualPoolJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
