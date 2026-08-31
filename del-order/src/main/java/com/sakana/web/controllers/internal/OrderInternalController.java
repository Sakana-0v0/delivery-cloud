package com.sakana.web.controllers.internal;

import com.sakana.dao.entity.Order;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.enums.OrderStatus;
import com.sakana.feign.vo.OrderSnapshotVO;
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
 * 调用方：del-payment（创建支付前取订单快照）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
@Tag(name = "订单-内部", description = "供其他微服务调用的订单查询接口（仅内网）")
public class OrderInternalController {

    private final OrderMapper orderMapper;

    /**
     * 取订单快照（用于创建支付）
     * <p>
     * 字段：id, orderNo, userId, payAmount, status, canPay
     */
    @GetMapping("/{orderId}")
    public R<OrderSnapshotVO> getOrderForPayment(@PathVariable Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || (order.getIsDeleted() != null && order.getIsDeleted() == 1)) {
            return R.fail(404, "订单不存在");
        }

        OrderSnapshotVO vo = new OrderSnapshotVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());

        OrderStatus os = OrderStatus.fromCode(order.getStatus());
        vo.setCanPay(os == OrderStatus.PENDING_PAYMENT);

        // username / email 留空：del-order 不持有 t_user 表（拆分后由 del-user 拥有）。
        // del-payment 通过创建支付时的 Redis 快照兜底；快照过期时通过 UserClient 兜底。
        return R.ok(vo);
    }
}