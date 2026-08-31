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
 * 订单支付成功事件（跨服务事件契约，下沉到 del-common）。
 * <p>
 * 由 del-payment 在 handleNotify 同事务里写 pay_outbox，再由 PayOutboxRelay 投递到
 * RabbitMQ exchange <code>payment.order.paid.exchange</code>（fanout）。
 * <p>
 * 消费方：
 * <ul>
 *   <li>del-order：改 t_order.status = PAID + pay_time</li>
 *   <li>del-message / del-notification：发邮件 + 写站内信（若独立）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一ID（消费端幂等键） */
    private String eventId;

    /** 支付流水号 */
    private String payNo;

    /** 订单号 */
    private String orderNo;

    /** 下单用户ID */
    private Long userId;

    /** 用户名（创建支付时快照，回调时不必再查用户库） */
    private String username;

    /** 用户邮箱（同上） */
    private String email;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    /** 支付宝交易号 */
    private String tradeNo;

    /** 事件发布时间（del-payment 侧） */
    private LocalDateTime occurredAt;
}
