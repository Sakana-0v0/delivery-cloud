package com.sakana.cart.web.controllers.internal;

import com.sakana.cart.services.CartService;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对内 API（仅内网可达，由网关 / NetworkPolicy 限制）。
 *
 * <p>调用方：del-order（下单成功后清空用户购物车）。
 *
 * <p>为什么不放在 del-order 内：购物车作为独立服务，跨服务的"清理购物车"动作应走
 * 对内的 Feign 接口，避免 del-order 直接操纵 Redis key（违反服务边界）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/cart")
@RequiredArgsConstructor
@Tag(name = "购物车-内部", description = "供其他微服务调用的购物车接口（仅内网）")
public class CartInternalController {

    private final CartService cartService;

    /**
     * 清空指定用户的购物车（通常在下单成功后调用）
     *
     * @param userId 用户ID
     */
    @DeleteMapping("/users/{userId}")
    public R<Void> clearUserCart(@PathVariable Long userId) {
        log.info("[内部调用] 清空用户购物车: userId={}", userId);
        cartService.clearCart(userId);
        return R.ok();
    }
}
