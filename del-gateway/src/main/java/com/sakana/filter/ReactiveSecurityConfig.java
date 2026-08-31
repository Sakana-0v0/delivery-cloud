package com.sakana.filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * del-gateway Security 配置
 *
 * <p>显式禁用 Spring Security WebFlux 的 CSRF 保护，
 * 因为我们用 AuthGlobalFilter 做路径级权限控制，不需要 CSRF token。
 *
 * <p>如果不显式提供，Spring Boot 会自动配置一个带 CSRF 保护的默认链，
 * 会拦截所有 POST/PUT/DELETE 请求，要求 CSRF token。
 */
@Configuration
@EnableWebFluxSecurity
public class ReactiveSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchange -> exchange.anyExchange().permitAll());
        return http.build();
    }
}
