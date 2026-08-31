package com.sakana.consumers.event;

import com.sakana.events.OrderCreatedPayload;
import com.sakana.integration.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 订单创建事件消费者
 * <p>
 * 监听订单创建事件，发送订单确认邮件。
 * <p>
 * 事件来源：del-order -> OrderOutboxRelay -> order.event.exchange (fanout)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventConsumer {

    private final EmailService emailService;

    /** 日期时间格式化 */
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 监听订单创建事件，发送订单确认邮件
     */
    @RabbitListener(queues = "${app.rabbit.order-event-queue:del-message.order.event}")
    public void onOrderCreated(OrderCreatedPayload payload) {
        log.info("[MQ-OrderCreated] 收到订单创建事件: eventId={}, orderNo={}, userId={}",
                payload.getEventId(), payload.getOrderNo(), payload.getUserId());

        if (payload.getEmail() == null || payload.getEmail().isBlank()) {
            log.warn("[MQ-OrderCreated] 用户邮箱为空，跳过邮件发送: orderNo={}", payload.getOrderNo());
            return;
        }

        try {
            String subject = String.format("【Delivery】订单 %s 已确认", payload.getOrderNo());
            String html = buildOrderCreatedEmailHtml(payload);
            emailService.sendHtml(payload.getEmail(), subject, html);
            log.info("[MQ-OrderCreated] 订单确认邮件发送成功: orderNo={}, email={}",
                    payload.getOrderNo(), payload.getEmail());
        } catch (Exception e) {
            log.error("[MQ-OrderCreated] 邮件发送失败: orderNo={}, error={}",
                    payload.getOrderNo(), e.getMessage(), e);
        }
    }

    /**
     * 构建订单确认邮件 HTML 内容
     */
    private String buildOrderCreatedEmailHtml(OrderCreatedPayload payload) {
        String orderTime = payload.getOrderTime() != null
                ? payload.getOrderTime().format(DT_FORMATTER) : "未知";
        String totalAmount = payload.getTotalAmount() != null
                ? formatAmount(payload.getTotalAmount()) : "0.00";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>订单确认通知</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <!-- Header -->
                        <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">订单确认通知</h1>
                            <p style="color: rgba(255,255,255,0.9); margin: 10px 0 0; font-size: 14px;">您的订单已成功创建</p>
                        </div>
                        
                        <!-- Content -->
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">
                                您好，<strong>%s</strong>：
                            </p>
                            <p style="color: #666666; font-size: 15px; line-height: 1.8;">
                                您的订单已成功创建，我们正在准备为您发货。
                            </p>
                            
                            <!-- Order Info Card -->
                            <div style="background: #f8f9fc; border-radius: 8px; padding: 20px; margin: 25px 0;">
                                <h3 style="color: #333333; font-size: 16px; margin: 0 0 15px; padding-bottom: 10px; border-bottom: 1px solid #eeeeee;">订单信息</h3>
                                <table style="width: 100%%; border-collapse: collapse;">
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">订单号</td>
                                        <td style="color: #667eea; font-size: 14px; font-weight: 600; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">下单时间</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">订单金额</td>
                                        <td style="color: #ff4d4f; font-size: 16px; font-weight: 600; text-align: right; padding: 8px 0;">¥%s</td>
                                    </tr>
                                </table>
                            </div>
                            
                            <p style="color: #666666; font-size: 14px; line-height: 1.6;">
                                您可以随时在我们的平台上追踪您的订单状态。我们会在订单发货后第一时间通知您。
                            </p>
                            
                            <div style="text-align: center; margin-top: 30px;">
                                <a href="#" style="display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff; padding: 12px 35px; border-radius: 25px; text-decoration: none; font-size: 14px; font-weight: 600;">查看订单详情</a>
                            </div>
                        </div>
                        
                        <!-- Footer -->
                        <div style="background: #f5f7fa; padding: 20px 30px; text-align: center; border-top: 1px solid #eeeeee;">
                            <p style="color: #999999; font-size: 12px; margin: 0;">
                                此邮件由系统自动发送，请勿回复。<br>
                                © 2024 Delivery 快递服务平台
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(payload.getUsername()),
                escapeHtml(payload.getOrderNo()),
                orderTime,
                totalAmount
        );
    }

    /**
     * 格式化金额为字符串（保留两位小数）
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    /**
     * HTML 转义，防止 XSS
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}