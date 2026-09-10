package com.sakana.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.util.JwtVerifier;
import com.sakana.web.vo.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局鉴权过滤器
 *
 * <p>执行顺序：
 * <ol>
 *   <li>OPTIONS 请求直接放行（CORS 预检）</li>
 *   <li>公开路径直接放行（登录、注册、商品浏览等）</li>
 *   <li>内部服务调用验证 X-Internal-Service-Token（/internal/**）</li>
 *   <li>提取 Authorization Bearer Token</li>
 *   <li>双密钥验签（先 user-pool，失败再 admin-pool）</li>
 *   <li>检查路径与角色是否匹配</li>
 *   <li>匹配则透传 X-User-* headers 后路由</li>
 *   <li>不匹配返回 401 或 403</li>
 * </ol>
 *
 * <p>安全说明：
 * <ul>
 *   <li>内部 API (/internal/**) 通过 X-Internal-Service-Token 验证，而非 JWT</li>
 *   <li>用户信息透传：下游服务从 X-User-* Header 获取，建议下游服务自行从 JWT 解析验证</li>
 *   <li>未来可添加网关签名机制增强安全性</li>
 * </ul>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GATEWAY_SOURCE = "del-gateway";

    /** 工具类用来解析 JWT Token */
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // 最高优先级：路由前执行
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. OPTIONS 预检请求直接放行
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 2. 公开路径放行
        if (PathRoleRule.isPublic(path)) {
            return chain.filter(exchange);
        }

        // 3. 内部服务调用验证（/internal/**）
        if (path.startsWith("/internal/") || path.startsWith("/api/v1/internal/")) {
            return validateInternalServiceCall(exchange, chain, request);
        }

        // 4. 提取 Token
        String token = extractToken(request);
        if (token == null) {
            return writeUnauthorized(exchange, "缺少访问令牌");
        }

        // 5. 双密钥验签
        TokenInfo tokenInfo = jwtVerifier.verify(token);
        if (tokenInfo == null) {
            return writeUnauthorized(exchange, "Token 无效或已过期");
        }

        // 6. 角色检查
        String denyReason = PathRoleRule.checkRole(path, tokenInfo.role());
        if (denyReason != null) {
            log.warn("[AuthFilter] 角色不足: path={}, role={}, reason={}",
                    path, tokenInfo.role(), denyReason);
            return writeForbidden(exchange, denyReason);
        }

        // 7. 透传用户信息
        // 注意：下游服务应自行从 JWT 解析验证，不应完全信任 X-User-* Header
        // 建议：下游服务从 X-User-* 获取基本信息，内部逻辑从 JWT 重新解析
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(tokenInfo.userId()))
                .header("X-User-Name", tokenInfo.username() == null ? "" : tokenInfo.username())
                .header("X-User-Role", tokenInfo.role() == null ? "" : tokenInfo.role())
                .header("X-Token-Source", tokenInfo.source())
                .build();

        log.debug("[AuthFilter] 通过: path={}, userId={}, role={}, source={}",
                path, tokenInfo.userId(), tokenInfo.role(), tokenInfo.source());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 验证内部服务调用
     *
     * <p>/internal/** 路径需要携带 X-Internal-Service-Token 头，
     * 用于微服务之间的内部调用鉴权
     */
    private Mono<Void> validateInternalServiceCall(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request) {

        String serviceToken = request.getHeaders()
                .getFirst(PathRoleRule.INTERNAL_SERVICE_TOKEN_HEADER);

        if (!PathRoleRule.checkInternalServiceToken(serviceToken)) {
            log.warn("[AuthFilter] 内部服务调用 Token 无效: path={}, token={}",
                    request.getURI().getPath(),
                    serviceToken == null ? "null" : "***");
            return writeForbidden(exchange, "内部服务调用未授权");
        }

        // Token 验证通过，透传内部服务角色
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Role", PathRoleRule.ROLE_INTERNAL_SERVICE)
                .header("X-Token-Source", "INTERNAL_SERVICE")
                .build();

        log.debug("[AuthFilter] 内部服务调用通过: path={}", request.getURI().getPath());
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        // Authorization : Bearer <token>
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        R<Void> body = R.fail(code, message);
        body.setSource(GATEWAY_SOURCE);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            log.error("[AuthFilter] 序列化响应失败", e);
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes();
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

