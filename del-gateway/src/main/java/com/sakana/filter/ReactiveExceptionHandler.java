package com.sakana.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.web.vo.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * del-gateway Reactive 异常处理器
 *
 * <p>Gateway 是 WebFlux 环境，del-common 的 GlobalExceptionHandler
 * 不会加载（@ConditionalOnWebApplication 限定 servlet），
 * 所以这里提供自己的实现做兜底。
 *
 * <p>实际触发场景：未在 AuthGlobalFilter 中拦截的异常
 * （如 Nacos 不可用、路由失败等），统一返回 R 格式。
 */
@Component
@Order(-2)
public class ReactiveExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveExceptionHandler.class);
    private static final String GATEWAY_SOURCE = "del-gateway";

    private final ObjectMapper objectMapper;

    public ReactiveExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("[GatewayException] 异常: {}", ex.getMessage(), ex);
        return writeJson(exchange, HttpStatus.INTERNAL_SERVER_ERROR, 9999, "网关异常");
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        R<Void> body = R.fail(code, message);
        body.setSource(GATEWAY_SOURCE);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
