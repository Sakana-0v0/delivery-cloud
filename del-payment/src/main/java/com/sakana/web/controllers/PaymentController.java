package com.sakana.web.controllers;

import com.sakana.security.SecurityUtil;
import com.sakana.services.PaymentService;
import com.sakana.web.vo.PaymentStatusVO;
import com.sakana.web.vo.PaymentVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * 支付接口（对外，前端调用）。
 * <p>
 * 路径前缀保持 {@code /api/v1/payments}，由网关路由到本服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "支付", description = "支付宝沙箱支付、回调、状态查询")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "创建支付（生成支付链接/表单）")
    public R<PaymentVO> createPayment(@RequestBody Map<String, Object> req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long orderId = Long.valueOf(req.get("orderId").toString());
        return R.ok(paymentService.createPayment(userId, orderId));
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "查询支付状态")
    public R<PaymentStatusVO> queryStatus(@PathVariable String orderNo) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(paymentService.queryStatus(userId, orderNo));
    }

    @PostMapping("/notify")
    @Operation(summary = "支付宝异步回调（公开验签）")
    public String handleNotify(HttpServletRequest request) {
        Map<String, String> params = new java.util.HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
        log.info("[支付回调] [Controller] 收到回调, params={}", params);
        return paymentService.handleNotify(params);
    }

    @GetMapping("/return")
    @Operation(summary = "支付宝同步跳转")
    public void handleReturn(@RequestParam Map<String, String> params,
                             HttpServletResponse response) throws IOException {
        // 同步跳转需带订单 ID 才能落到前端 /orders/:id 路由详情页
        log.info("[支付同步跳转] params={}", params);
        String orderNo = params.get("out_trade_no");
        Long orderId = paymentService.findOrderIdByOrderNo(orderNo);
        response.sendRedirect("http://localhost:5173/orders/" + (orderId == null ? orderNo : orderId));
    }
}