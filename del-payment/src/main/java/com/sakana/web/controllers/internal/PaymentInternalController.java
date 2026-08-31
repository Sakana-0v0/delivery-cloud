package com.sakana.web.controllers.internal;

import com.sakana.services.PaymentService;
import com.sakana.web.vo.PaymentInternalVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对内 API（仅内网可达，由网关 / NetworkPolicy 限制）。
 * <p>
 * 调用方：del-order（订单详情带支付信息）、del-comment（评价前校验是否已支付）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
@Tag(name = "支付-内部", description = "供其他微服务调用的支付查询接口（仅内网）")
public class PaymentInternalController {

    private final PaymentService paymentService;

    @GetMapping("/{orderNo}")
    public R<PaymentInternalVO> getByOrderNo(@PathVariable String orderNo) {
        log.info("[内部API] GET /internal/payments/{}", orderNo);
        return R.ok(paymentService.queryByOrderNo(orderNo));
    }

    @GetMapping("/{orderNo}/paid")
    public R<Boolean> isPaid(@PathVariable String orderNo) {
        log.info("[内部API] GET /internal/payments/{}/paid", orderNo);
        return R.ok(paymentService.isOrderPaid(orderNo));
    }
}
