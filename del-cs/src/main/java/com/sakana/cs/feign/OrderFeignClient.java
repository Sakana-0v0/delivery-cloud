package com.sakana.cs.feign;

import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调 del-order 的订单接口（内部服务调用，走 /internal/** 路径）
 * 鉴权：X-Internal-Service-Token 由 InternalServiceFeignInterceptor 自动注入
 */
@FeignClient(name = "del-order", contextId = "csOrderFeignClient", path = "/api/v1")
public interface OrderFeignClient {

    /**
     * ★ BUG-019：内部 - 分页查询用户订单（跨线程调用，通过 URL 参数传 userId）
     */
    @GetMapping("/internal/orders")
    R<OrderPageResp> getUserOrders(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    );

    /**
     * ★ BUG-019：内部 - 通过 orderNo 查询单个订单（跨线程调用，通过 URL 参数传 userId）
     */
    @GetMapping("/internal/orders/by-order-no")
    R<OrderVO> getByOrderNo(
            @RequestParam Long userId,
            @RequestParam String orderNo
    );
}
