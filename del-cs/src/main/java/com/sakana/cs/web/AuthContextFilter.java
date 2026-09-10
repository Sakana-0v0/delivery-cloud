package com.sakana.cs.web;

import com.sakana.cs.context.AuthContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求过滤器：自动从 Authorization header 提取 token 并存入 AuthContext
 *
 * 注意：对于 SSE 流式接口（/chat），不在 Filter 中清理 AuthContext，
 * 因为 SSE 是异步的，工具执行发生在 Filter 返回之后。
 * AuthContext 清理由 ChatController 的 SSE 回调处理（onCompletion/onTimeout/onError）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 提取 Authorization header 并存入 AuthContext
        String authHeader = httpRequest.getHeader("Authorization");
        String token = AuthContext.extractBearerToken(authHeader);
        if (token != null) {
            AuthContext.setToken(token);
            log.debug("[AuthContextFilter] 设置 token, path={}", path);
        }

        // 对于 SSE 流式接口，Filter 只负责设置 AuthContext，清理由 SSE 回调处理
        boolean isSse = path.startsWith("/api/v1/cs/chat");
        if (isSse) {
            try {
                chain.doFilter(request, response);
            } catch (Exception e) {
                log.error("[AuthContextFilter] SSE 路径异常: {}", e.getMessage());
                throw e;
            }
            // 不在 finally 中清理！由 ChatController 的 SSE 回调清理
        } else {
            try {
                chain.doFilter(request, response);
            } finally {
                AuthContext.clear();
            }
        }
    }
}
