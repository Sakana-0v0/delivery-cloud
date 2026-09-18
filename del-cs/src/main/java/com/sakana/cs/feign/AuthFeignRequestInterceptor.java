package com.sakana.cs.feign;

import com.sakana.cs.context.AuthContext;
import com.sakana.cs.context.ChatContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：把当前用户的 Authorization header 注入到下游 Feign 调用。
 *
 * <p>★ P0-UserAuth重构：补充 ChatContextHolder fallback。
 * 当 Tool 在 SSE 异步线程中执行时，AuthContext（HttpFilter 设置）会丢失；
 * 此时从 ChatContextHolder（ChatController 入口设置）回退读取，
 * 让 del-order/del-product 始终能拿到真实用户 JWT 完成 X-User-* 鉴权。
 *
 * <p>如果两者都为空（理论上不会发生），跳过注入；匿名接口（公开搜索等）依赖业务侧放行。
 */
@Slf4j
@Component
public class AuthFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 优先从 AuthContext 取（HTTP 请求线程）；取不到回退到 ChatContextHolder（SSE 异步线程）
        String token = AuthContext.getToken();
        if (token == null || token.isBlank()) {
            token = ChatContextHolder.getToken();
        }
        if (token != null && !token.isBlank()) {
            template.header("Authorization", "Bearer " + token);
            log.debug("[FeignInterceptor] 注入 Authorization header");
        } else {
            log.debug("[FeignInterceptor] 无 token，跳过注入（可能是匿名请求）");
        }
    }
}