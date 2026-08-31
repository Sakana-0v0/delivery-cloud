package com.sakana.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 内部服务认证过滤器
 *
 * <p>用于保护内部 API 端点（/internal/**），确保只有来自其他微服务的请求才能访问。
 * <p>
 * 安全模型：
 * <ul>
 *   <li>生产环境：建议使用服务网格（Istio/Linkerd）的 mTLS 或服务账号 Token</li>
 *   <li>当前实现：检查 X-Internal-Service-Token 头是否与配置的服务密钥匹配</li>
 * </ul>
 *
 * <p>配置项（通过 application.yml）：
 * <ul>
 *   <li>security.internal.service-token: 内部服务间通信的共享密钥</li>
 * </ul>
 */
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);

    /**
     * 内部服务调用标识头
     */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    /**
     * 内部服务标识头（调用方服务名）
     */
    public static final String HEADER_INTERNAL_SERVICE = "X-Internal-Service-Name";

    /**
     * 默认的服务间共享密钥（生产环境应通过环境变量或密钥管理服务配置）
     * <p>
     * 警告：此为示例密钥，生产环境必须使用强随机密钥并安全存储
     */
    private static final String DEFAULT_SERVICE_TOKEN = "internal-service-secret-key-2024";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 只对 /internal/** 路径进行处理
        if (!requestPath.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceToken = request.getHeader(HEADER_INTERNAL_TOKEN);
        String callerService = request.getHeader(HEADER_INTERNAL_SERVICE);

        // 验证内部服务 Token
        if (isValidInternalRequest(serviceToken)) {
            // 创建内部服务认证信息
            LoginUser internalService = new LoginUser(
                    -1L,  // 内部服务无用户ID
                    callerService != null ? callerService : "unknown-internal-service",
                    "INTERNAL_SERVICE"
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            internalService,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("[InternalAuth] 内部服务调用认证成功: path={}, caller={}", requestPath, callerService);
        } else {
            log.warn("[InternalAuth] 内部服务调用认证失败: path={}, caller={}, hasToken={}",
                    requestPath, callerService, StringUtils.hasText(serviceToken));

            // 认证失败，返回 401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized: Invalid internal service token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 验证内部服务请求是否有效
     * <p>
     * 当前实现：简单检查 Token 是否与配置值匹配
     * <p>
     * 生产环境建议：
     * - 使用 JWT 签名验证
     * - 使用服务网格的 mTLS
     * - 使用专门的密钥管理服务（如 Vault）
     *
     * @param token 请求中的 Token
     * @return 是否有效
     */
    private boolean isValidInternalRequest(String token) {
        // 在生产环境中，应该从配置或密钥管理服务获取正确的 Token
        // 当前使用简单的字符串比较
        return DEFAULT_SERVICE_TOKEN.equals(token);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 对所有请求进行检查，但只在 /internal/** 路径生效
        return false;
    }
}
