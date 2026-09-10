package com.sakana.web.controllers;

import com.sakana.dto.request.OrderCreateReq;
import com.sakana.security.SecurityUtil;
import com.sakana.services.OrderService;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（C 端）
 */
@RestController
@RequestMapping("/api/v1/user/orders")
@RequiredArgsConstructor
@Tag(name = "订单", description = "创建、查询、取消、确认收货")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "创建订单")
    public R<OrderVO> createOrder(@Valid @RequestBody OrderCreateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(orderService.createOrder(userId, req));
    }

    @GetMapping
    @Operation(summary = "我的订单")
    public R<OrderPageResp> getMyOrders(
            @Parameter(description = "订单状态：1待支付 2已支付 3配送中 4已完成 5已取消")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(orderService.getMyOrders(userId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public R<OrderVO> getOrderDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(orderService.getOrderDetail(userId, id));
    }
    /**
     * ★ BUG-018：通过业务订单号（orderNo）查询订单
     */
    @GetMapping("/by-order-no/{orderNo}")
    @Operation(summary = "通过订单号查询订单")
    public R<OrderVO> getByOrderNo(@PathVariable String orderNo) {
        Long userId = SecurityUtil.getCurrentUserId();
        OrderVO order = orderService.getByOrderNo(userId, orderNo);
        return order != null ? R.ok(order) : R.fail(404, "订单不存在");
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消订单（仅待支付可取消）")
    public R<Void> cancelOrder(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        orderService.cancelOrder(userId, id);
        return R.ok();
    }

    @PutMapping("/{id}/confirm")
    @Operation(summary = "确认收货（仅配送中可确认）")
    public R<Void> confirmOrder(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        orderService.confirmOrder(userId, id);
        return R.ok();
    }
}
