package com.sakana.web.controllers;

import com.sakana.services.OrderService;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ BUG-019：内部服务调用端点（del-cs Feign 调用走这里）
 * 路径：/api/v1/internal/orders/**
 * 鉴权：X-Internal-Service-Token（InternalServiceAuthFilter 已处理）
 */
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
@Tag(name = "内部订单接口", description = "供其他服务内部调用，无需用户 JWT")
public class InternalOrderController {

    private final OrderService orderService;

    /**
     * ★ BUG-019：内部 - 分页查询用户订单
     * @param userId  用户ID（从请求参数传入，Feign 跨线程调用）
     * @param page    页码
     * @param size    每页大小
     */
    @GetMapping
    @Operation(summary = "内部-查询用户订单列表")
    public R<OrderPageResp> getUserOrders(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "5") Integer size) {
        return R.ok(orderService.getMyOrders(userId, null, page, size));
    }

    /**
     * ★ BUG-019：内部 - 通过 orderNo 查询单个订单
     * @param userId  用户ID
     * @param orderNo 业务订单号
     */
    @GetMapping("/by-order-no")
    @Operation(summary = "内部-通过订单号查询订单")
    public R<OrderVO> getByOrderNo(
            @RequestParam Long userId,
            @RequestParam String orderNo) {
        OrderVO order = orderService.getByOrderNo(userId, orderNo);
        return order != null ? R.ok(order) : R.fail(404, "订单不存在");
    }
}
