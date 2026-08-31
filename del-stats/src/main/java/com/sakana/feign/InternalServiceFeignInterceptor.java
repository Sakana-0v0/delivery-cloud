package com.sakana.feign;

import com.sakana.security.InternalServiceAuthFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Feign 内部服务调用拦截器
 * <p>
 * 为所有 Feign 请求添加内部服务认证头，确保可以访问各服务的 /internal/** 端点。
 */
@Component
public class InternalServiceFeignInterceptor implements RequestInterceptor {

    /**
     * 默认的内部服务名称（可配置）
     */
    private static final String INTERNAL_SERVICE_NAME = "del-stats";

    /**
     * 默认的内部服务 Token（应与 InternalServiceAuthFilter 中的 DEFAULT_SERVICE_TOKEN 一致）
     * <p>
     * 生产环境建议：从配置中心或密钥管理服务获取
     */
    private static final String INTERNAL_SERVICE_TOKEN = "internal-service-secret-key-2024";

    @Override
    public void apply(RequestTemplate template) {
        // 添加内部服务认证头
        template.header(InternalServiceAuthFilter.HEADER_INTERNAL_TOKEN, INTERNAL_SERVICE_TOKEN);
        template.header(InternalServiceAuthFilter.HEADER_INTERNAL_SERVICE, INTERNAL_SERVICE_NAME);
    }
}
