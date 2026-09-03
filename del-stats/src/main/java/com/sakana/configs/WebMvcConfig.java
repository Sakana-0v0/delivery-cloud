package com.sakana.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>注册访问日志拦截器，拦截所有请求（除 actuator 和内部接口外）
 */
@Configuration
@RequiredArgsConstructor
@EnableAsync
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final AccessLogInterceptor accessLogInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/actuator/**",
                    "/internal/**"
                );
    }
}