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
 */
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";
    public static final String HEADER_INTERNAL_SERVICE = "X-Internal-Service-Name";
    public static final String DEFAULT_SERVICE_TOKEN = "internal-service-secret-key-2024";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        if (!requestPath.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceToken = request.getHeader(HEADER_INTERNAL_TOKEN);
        String callerService = request.getHeader(HEADER_INTERNAL_SERVICE);

        if (isValidInternalRequest(serviceToken)) {
            LoginUser internalService = new LoginUser(
                    -1L,
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

            log.debug("[InternalAuth] internal auth success: path={}, caller={}", requestPath, callerService);
        } else {
            log.warn("[InternalAuth] internal auth failed: path={}, caller={}, hasToken={}",
                    requestPath, callerService, StringUtils.hasText(serviceToken));

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized: Invalid internal service token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidInternalRequest(String token) {
        return DEFAULT_SERVICE_TOKEN.equals(token);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
