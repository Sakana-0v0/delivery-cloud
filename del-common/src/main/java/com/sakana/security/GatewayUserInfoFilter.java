package com.sakana.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 网关用户信息过滤器
 *
 * <p>处理经过 del-gateway 认证后的请求：
 * <ul>
 *   <li>网关 AuthGlobalFilter 已完成 Token 验签</li>
 *   <li>网关将用户信息放入 X-User-* 请求头传递给下游服务</li>
 *   <li>本过滤器读取这些头并设置 SecurityContext</li>
 * </ul>
 *
 * <p>注意：本过滤器必须在 {@link DualPoolJwtAuthenticationFilter} 之前执行，
 * 因为后者会读取 Authorization header（网关已剥离），导致无法认证。
 */
@Component
public class GatewayUserInfoFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayUserInfoFilter.class);

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_TOKEN_SOURCE = "X-Token-Source";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userIdStr = request.getHeader(HEADER_USER_ID);
        String username = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);
        String tokenSource = request.getHeader(HEADER_TOKEN_SOURCE);

        if (StringUtils.hasText(userIdStr) && StringUtils.hasText(role)) {
            try {
                Long userId = Long.parseLong(userIdStr);
                LoginUser loginUser = new LoginUser(userId, username, role);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                loginUser,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("[GatewayUserInfo] 认证成功: userId={}, username={}, role={}, source={}",
                        userId, username, role, tokenSource);

            } catch (NumberFormatException e) {
                log.warn("[GatewayUserInfo] X-User-Id 格式错误: {}", userIdStr);
            }
        } else {
            log.debug("[GatewayUserInfo] 未收到网关用户信息头，跳过");
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只有从网关来的请求才处理（检查 X-Token-Source 头是否存在）
        return !StringUtils.hasText(request.getHeader(HEADER_TOKEN_SOURCE));
    }
}
