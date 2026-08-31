package com.sakana.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.web.vo.R;
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
import java.util.function.Function;

/**
 * 双密钥 JWT 认证过滤器（适用同时暴露 C 端 + B 端服务）
 *
 * <p>行为：
 * <ul>
 *   <li>从 Authorization: Bearer xxx 提取 token</li>
 *   <li>调用 {@link DualPoolJwtVerifier} 校验（user-pool 优先，失败再试 admin-pool）</li>
 *   <li>校验通过后把用户信息写入 SecurityContext，principal 统一为 {@link LoginUser}</li>
 *   <li>校验失败返回 401（不阻断没带 token 的请求，由 SecurityConfig 决定是否需要登录）</li>
 * </ul>
 *
 * <p>黑名单：默认无（多数服务只校验 token 合法性，不维护黑名单）。
 * 需要黑名单的服务可在 SecurityConfig 里注入 blacklistChecker。
 */
@Component
public class DualPoolJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DualPoolJwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final DualPoolJwtVerifier verifier;
    private final ObjectMapper objectMapper;
    /**
     * 可选：黑名单校验函数（jti -> 是否已拉黑）。无状态服务可不注入。
     */
    private Function<String, Boolean> blacklistChecker;

    public DualPoolJwtAuthenticationFilter(DualPoolJwtVerifier verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    /**
     * 由服务按需注入黑名单
     */
    public void setBlacklistChecker(Function<String, Boolean> blacklistChecker) {
        this.blacklistChecker = blacklistChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            DualPoolJwtVerifier.VerifiedToken verified = verifier.verify(token);
            if (verified == null) {
                sendUnauthorizedResponse(response, 401, "Token无效或已过期");
                return;
            }

            // 黑名单
            if (blacklistChecker != null && Boolean.TRUE.equals(blacklistChecker.apply(verified.jti()))) {
                sendUnauthorizedResponse(response, 401, "Token已失效");
                return;
            }

            LoginUser loginUser = new LoginUser(verified.userId(), verified.username(), verified.role());

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    loginUser, null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + verified.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("[DualPoolJWT] 认证通过: pool={}, userId={}, role={}, path={}",
                    verified.pool(), verified.userId(), verified.role(), request.getRequestURI());

        } catch (Exception e) {
            log.warn("[DualPoolJWT] 认证异常: {}", e.getMessage());
            sendUnauthorizedResponse(response, 401, "Token无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        R<Void> result = R.fail(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/doc.html");
    }
}
