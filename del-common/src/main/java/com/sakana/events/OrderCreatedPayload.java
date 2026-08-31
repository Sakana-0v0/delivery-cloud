package com.sakana.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单创建成功事件。
 * <p>
 * 由 del-order 写 order_outbox，再由 OrderOutboxRelay 投递到
 * RabbitMQ fanout exchange <code>order.event.exchange</code>。
 * <p>
 * 消费方：del-message（发送订单确认邮件）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一ID（消费端幂等键） */
    private String eventId;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 用户邮箱 */
    private String email;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单创建时间 */
    private LocalDateTime orderTime;

    /** 事件发布时间 */
    private LocalDateTime occurredAt;
}
