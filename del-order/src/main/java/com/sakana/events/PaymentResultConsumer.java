package com.sakana.events;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.sakana.configs.RabbitMQConfig;
import com.sakana.dao.entity.Order;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 监听 del-payment 的 payment.order.paid 事件，把订单状态改为 PAID。
 * <p>
 * 替代原单体 PaymentServiceImpl.handleNotify() 里直接调用 orderMapper.updateById 的反向耦合。
 * <p>
 * 幂等：当前订单已是 PAID 则跳过；非法状态机（已取消 / 已完成）跳过不重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderMapper orderMapper;

    @RabbitListener(
            queues = RabbitMQConfig.ORDER_PAID_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onPaid(@Payload OrderPaidPayload payload,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, payload.getOrderNo()));
            if (order == null) {
                log.warn("[order.paid] 订单不存在 orderNo={}", payload.getOrderNo());
                channel.basicAck(tag, false);
                return;
            }
            // 幂等
            if (order.getStatus() != null && order.getStatus() == OrderStatus.PAID.getCode()) {
                log.info("[order.paid] 订单已是 PAID，跳过 orderNo={}", payload.getOrderNo());
                channel.basicAck(tag, false);
                return;
            }
            // 状态机合法性（防御）
            OrderStatus current = OrderStatus.fromCode(order.getStatus());
            if (current != OrderStatus.PENDING_PAYMENT) {
                log.warn("[order.paid] 订单状态非法跳转 current={}, orderNo={}",
                        current, payload.getOrderNo());
                channel.basicAck(tag, false);
                return;     // 不抛异常，避免反复重投
            }

            order.setStatus(OrderStatus.PAID.getCode());
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("[order.paid] 订单状态已更新: orderNo={} -> PAID", payload.getOrderNo());

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[order.paid] 处理失败 eventId={}", payload.getEventId(), e);
            channel.basicNack(tag, false, true);  // 重试
        }
    }
}
