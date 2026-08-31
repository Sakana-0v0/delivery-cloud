package com.sakana.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.Order;
import com.sakana.dao.entity.OrderItem;
import com.sakana.dao.mapper.OrderItemMapper;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单定时任务（归属于 del-order 内部）
 *
 * <p>由 {@link com.sakana.OrderApplication} 的 {@code @EnableScheduling} 启用。
 * <p>分布式环境下，多副本时建议用 ShedLock 或 Redis 分布式锁防重复执行（当前为单机演示）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduledJobs {

    /** 订单超时取消：15 分钟未支付 */
    private static final long ORDER_TIMEOUT_MINUTES = 15L;

    /** 自动发货：支付后 30 秒 */
    private static final long AUTO_SHIP_DELAY_SECONDS = 30L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 每 60 秒扫描一次超时未支付订单，自动取消并回滚库存
     */
    @Scheduled(fixedRate = 60_000L)
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                .lt(Order::getCreateTime, deadline)
                .eq(Order::getIsDeleted, 0);

        List<Order> timeoutOrders = orderMapper.selectList(wrapper);
        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("[超时取消任务] 发现{}个超时订单", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

                for (OrderItem item : items) {
                    try {
                        orderMapper.increaseStock(item.getProductId(), item.getQuantity());
                    } catch (Exception ex) {
                        log.warn("[超时取消] 回滚库存失败 productId={} qty={}: {}",
                                item.getProductId(), item.getQuantity(), ex.getMessage());
                    }
                }

                order.setStatus(OrderStatus.CANCELLED.getCode());
                order.setCancelTime(LocalDateTime.now());
                orderMapper.updateById(order);

                long mins = Duration.between(order.getCreateTime(), LocalDateTime.now()).toMinutes();
                log.info("[超时取消] orderId={}, orderNo={}, 超时{}分钟",
                        order.getId(), order.getOrderNo(), mins);
            } catch (Exception e) {
                log.error("[超时取消] orderId={} 处理失败: {}", order.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 每 30 秒扫描一次已支付订单，超过 30 秒未发货则自动进入配送中
     */
    @Scheduled(fixedRate = 30_000L)
    @Transactional(rollbackFor = Exception.class)
    public void autoShipOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(AUTO_SHIP_DELAY_SECONDS);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, OrderStatus.PAID.getCode())
                .lt(Order::getPayTime, deadline)
                .eq(Order::getIsDeleted, 0);

        List<Order> waitShipOrders = orderMapper.selectList(wrapper);
        if (waitShipOrders.isEmpty()) {
            return;
        }

        log.info("[自动发货任务] 发现{}个待发货订单", waitShipOrders.size());

        for (Order order : waitShipOrders) {
            try {
                order.setStatus(OrderStatus.SHIPPING.getCode());
                order.setShipTime(LocalDateTime.now());
                orderMapper.updateById(order);

                long secs = Duration.between(order.getPayTime(), LocalDateTime.now()).toSeconds();
                log.info("[自动发货] orderId={}, orderNo={}, 支付后{}秒",
                        order.getId(), order.getOrderNo(), secs);
            } catch (Exception e) {
                log.error("[自动发货] orderId={} 失败: {}", order.getId(), e.getMessage(), e);
            }
        }
    }
}
