package com.sakana.consumers;

import com.sakana.events.OrderPaidPayload;
import com.sakana.integration.email.EmailService;
import com.sakana.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单事件消费者（订阅 del-order / del-payment 发布的事件）
 *
 * <p>职责：
 * <ul>
 *   <li>落库：写入 t_message（用户站内信）</li>
 *   <li>触发：调用 EmailService 发邮件（异步失败不影响站内信）</li>
 * </ul>
 *
 * <p>幂等策略：当前用 orderNo 去重；如果未来事件量级大，应改为本地消息表 + 定时扫描补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final MessageService messageService;
    private final EmailService emailService;

    @Value("${app.rabbit.order-event-queue:del-message.order.event}")
    private String orderEventQueue;

    /**
     * 订阅订单已支付事件
     * 队列：del-message.order.event（绑定到 order.event.exchange fanout）
     */
    @RabbitListener(queues = "${app.rabbit.order-paid-queue:del-message.order.paid}")
    public void onOrderPaid(OrderPaidPayload payload) {
        log.info("[MQ-OrderPaid] 收到订单已支付事件: orderNo={}, userId={}", 
                payload.getOrderNo(), payload.getUserId());

        // 1. 写站内信（落库）
        try {
            messageService.saveMessage(
                    payload.getUserId(),
                    "order",
                    "订单已支付",
                    String.format("您的订单 %s 已完成支付，实付金额：%s 元。", 
                            payload.getOrderNo(), 
                            payload.getPayAmount() != null ? payload.getPayAmount().toString() : "0"),
                    payload.getOrderNo()
            );
        } catch (Exception e) {
            log.error("[MQ-OrderPaid] 写站内消息失败 orderNo={}: {}", payload.getOrderNo(), e.getMessage(), e);
        }

        // 2. 发送支付成功邮件（可选，失败不影响主流程）
        if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
            try {
                String subject = String.format("【Delivery】订单 %s 支付成功", payload.getOrderNo());
                String html = buildPaymentSuccessEmailHtml(payload);
                emailService.sendHtml(payload.getEmail(), subject, html);
                log.info("[MQ-OrderPaid] 支付成功邮件已发送: orderNo={}", payload.getOrderNo());
            } catch (Exception e) {
                log.error("[MQ-OrderPaid] 邮件发送失败 orderNo={}: {}", payload.getOrderNo(), e.getMessage(), e);
            }
        }
    }

    /**
     * 构建支付成功邮件 HTML
     */
    private String buildPaymentSuccessEmailHtml(OrderPaidPayload payload) {
        String paidTime = payload.getPaidAt() != null 
                ? payload.getPaidAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "未知";
        String payAmount = payload.getPayAmount() != null 
                ? payload.getPayAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString()
                : "0.00";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>支付成功通知</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <div style="background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%); padding: 30px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">支付成功</h1>
                        </div>
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">
                                您好，<strong>%s</strong>：
                            </p>
                            <p style="color: #666666; font-size: 15px; line-height: 1.8;">
                                您的订单已支付成功，我们正在准备发货。
                            </p>
                            <div style="background: #f8f9fc; border-radius: 8px; padding: 20px; margin: 25px 0;">
                                <table style="width: 100%%; border-collapse: collapse;">
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">订单号</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">支付金额</td>
                                        <td style="color: #52c41a; font-size: 18px; font-weight: 600; text-align: right; padding: 8px 0;">¥%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">支付时间</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                </table>
                            </div>
                            <p style="color: #999999; font-size: 13px; line-height: 1.6;">
                                如有疑问，请联系客服咨询。
                            </p>
                        </div>
                        <div style="background: #f5f7fa; padding: 20px 30px; text-align: center; border-top: 1px solid #eeeeee;">
                            <p style="color: #999999; font-size: 12px; margin: 0;">© 2024 Delivery 快递服务平台</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(payload.getUsername()),
                escapeHtml(payload.getOrderNo()),
                payAmount,
                paidTime
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;").replace("'", "&#x27;");
    }
}