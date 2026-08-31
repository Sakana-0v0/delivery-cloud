package com.sakana.feign;

import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * del-payment → del-order 的内部 API。
 * <p>
 * 仅用于创建支付前的订单快照读取。回调链路不依赖此 Feign（解耦关键设计）。
 * 通过 Nacos 服务发现 (name=del-order) 自动负载均衡。
 */
@FeignClient(name = "del-order", contextId = "paymentOrderClient", fallbackFactory = OrderClientFallback.class)
public interface OrderClient {

    /**
     * 获取订单快照（用于创建支付）
     *
     * @param orderId 订单主键ID（不是 orderNo）
     */
    @GetMapping("/internal/orders/{orderId}")
    R<OrderSnapshotVO> getOrderForPayment(@PathVariable("orderId") Long orderId);
}
