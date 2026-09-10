package com.sakana.cs.feign;

import com.sakana.security.InternalServiceAuthFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Feign 配置：确保内部服务调用携带 X-Internal-Service-Token
 * 优先级高于 del-common 的全局 InternalServiceFeignInterceptor
 */
@Slf4j
@Configuration
public class FeignInternalConfig {

    @Bean
    public RequestInterceptor internalServiceRequestInterceptor() {
        return (RequestTemplate template) -> {
            if (template.url().contains("/internal/")) {
                template.header(InternalServiceAuthFilter.HEADER_INTERNAL_TOKEN,
                        InternalServiceAuthFilter.DEFAULT_SERVICE_TOKEN);
                log.debug("[Feign] 注入内部服务Token: url={}", template.url());
            }
        };
    }
}

