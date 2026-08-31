package com.sakana.consumers.event;

import com.sakana.events.RegisterEventPayload;
import com.sakana.integration.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 用户注册事件消费者
 * <p>
 * 监听用户注册事件，发送注册欢迎邮件。
 * <p>
 * 事件来源：del-user -> UserEventOutboxRelay -> user.event.exchange (fanout)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterEventConsumer {

    private final EmailService emailService;

    /** 日期时间格式化 */
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 监听用户注册事件，发送注册欢迎邮件
     */
    @RabbitListener(queues = "${app.rabbit.user-event-queue:del-message.user.event}")
    public void onRegister(RegisterEventPayload payload) {
        log.info("[MQ-Register] 收到注册事件: eventId={}, userId={}, username={}",
                payload.getEventId(), payload.getUserId(), payload.getUsername());

        // 幂等检查：eventId 可用于分布式锁，此处简化处理
        if (payload.getEmail() == null || payload.getEmail().isBlank()) {
            log.warn("[MQ-Register] 用户邮箱为空，跳过邮件发送: userId={}", payload.getUserId());
            return;
        }

        try {
            String subject = "【Delivery】欢迎加入快递服务平台";
            String html = buildWelcomeEmailHtml(payload);
            emailService.sendHtml(payload.getEmail(), subject, html);
            log.info("[MQ-Register] 注册欢迎邮件发送成功: userId={}, email={}", payload.getUserId(), payload.getEmail());
        } catch (Exception e) {
            log.error("[MQ-Register] 邮件发送失败: userId={}, error={}", payload.getUserId(), e.getMessage(), e);
            // 邮件发送失败不影响消息消费，消息已被 RabbitMQ ack
        }
    }

    /**
     * 构建注册欢迎邮件 HTML 内容
     */
    private String buildWelcomeEmailHtml(RegisterEventPayload payload) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>欢迎加入 Delivery</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <!-- Header -->
                        <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 600;">欢迎加入 Delivery</h1>
                            <p style="color: rgba(255,255,255,0.9); margin: 10px 0 0; font-size: 16px;">让快递服务更简单</p>
                        </div>
                        
                        <!-- Content -->
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">
                                您好，<strong>%s</strong>：
                            </p>
                            <p style="color: #666666; font-size: 15px; line-height: 1.8;">
                                感谢您注册 Delivery 快递服务平台！您的账号已成功创建。
                            </p>
                            
                            <!-- Info Card -->
                            <div style="background: #f8f9fc; border-radius: 8px; padding: 20px; margin: 25px 0;">
                                <table style="width: 100%%; border-collapse: collapse;">
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">注册时间</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">账号</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                </table>
                            </div>
                            
                            <!-- Feature Highlights -->
                            <div style="margin: 30px 0;">
                                <h3 style="color: #333333; font-size: 16px; margin-bottom: 15px;">您可以享受以下服务：</h3>
                                <ul style="color: #666666; font-size: 14px; line-height: 2; padding-left: 20px; margin: 0;">
                                    <li>便捷的快递下单与追踪</li>
                                    <li>实时物流状态推送</li>
                                    <li>订单历史管理</li>
                                    <li>专属客服支持</li>
                                </ul>
                            </div>
                            
                            <div style="text-align: center; margin-top: 35px;">
                                <a href="#" style="display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #ffffff; padding: 14px 40px; border-radius: 25px; text-decoration: none; font-size: 15px; font-weight: 600;">立即体验</a>
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
                payload.getRegisterTime() != null ? payload.getRegisterTime().format(DT_FORMATTER) : "未知",
                escapeHtml(payload.getUsername())
        );
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