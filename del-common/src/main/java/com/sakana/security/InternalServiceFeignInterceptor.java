package com.sakana.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feign 拦截器：自动为内部服务间调用添加 Token 和服务名头部。
 *
 * <p>作用：当 Feign 客户端调用 /internal/** 路径时，自动添加：
 * <ul>
 *   <li>X-Internal-Service-Token: 内部服务密钥</li>
 *   <li>X-Internal-Service-Name: 调用方服务名（从 spring.application.name 读取）</li>
 * </ul>
 *
 * <p>被 {@link InternalServiceAuthFilter} 校验通过后才能访问 /internal/** 端点。
 */
@Slf4j
@Component
public class InternalServiceFeignInterceptor implements RequestInterceptor {

    @Value("${spring.application.name:unknown-service}")
    private String callerServiceName;

    @Override
    public void apply(RequestTemplate template) {
        if (template.url() != null && template.url().contains("/internal/")) {
            template.header(InternalServiceAuthFilter.HEADER_INTERNAL_TOKEN,
                    InternalServiceAuthFilter.DEFAULT_SERVICE_TOKEN);
            template.header(InternalServiceAuthFilter.HEADER_INTERNAL_SERVICE, callerServiceName);
            log.debug("[Feign] 自动注入内部服务头: url={}, caller={}",
                    template.url(), callerServiceName);
        }
    }
}
