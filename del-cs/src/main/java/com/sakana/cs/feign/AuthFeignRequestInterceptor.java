package com.sakana.cs.feign;

import com.sakana.cs.context.AuthContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：从 AuthContext 取 token，注入到所有 Feign 请求头
 * 解决 del-cs 调用 del-order /api/v1/user/** 时缺少认证 token 的问题
 */
@Slf4j
@Component
public class AuthFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String token = AuthContext.getToken();
        if (token != null && !token.isBlank()) {
            template.header("Authorization", "Bearer " + token);
            log.debug("[FeignInterceptor] 注入 Authorization header");
        } else {
            log.debug("[FeignInterceptor] 无 token，跳过注入（可能是匿名请求）");
        }
    }
}