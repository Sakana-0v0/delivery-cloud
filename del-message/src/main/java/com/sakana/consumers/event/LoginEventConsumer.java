package com.sakana.consumers.event;

import com.sakana.events.LoginEventPayload;
import com.sakana.integration.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 用户登录事件消费者
 * <p>
 * 监听用户登录事件，发送登录成功通知邮件。
 * <p>
 * 事件来源：del-user -> UserEventOutboxRelay -> user.event.exchange (fanout)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventConsumer {

    private final EmailService emailService;

    /** 日期时间格式化 */
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 监听用户登录事件，发送登录成功邮件
     */
    @RabbitListener(queues = "${app.rabbit.user-event-queue:del-message.user.event}")
    public void onLogin(LoginEventPayload payload) {
        log.info("[MQ-Login] 收到登录事件: eventId={}, userId={}, username={}",
                payload.getEventId(), payload.getUserId(), payload.getUsername());

        // 幂等检查：eventId 可用于分布式锁，此处简化处理
        if (payload.getEmail() == null || payload.getEmail().isBlank()) {
            log.warn("[MQ-Login] 用户邮箱为空，跳过邮件发送: userId={}", payload.getUserId());
            return;
        }

        try {
            String subject = "【Delivery】登录成功通知";
            String html = buildLoginEmailHtml(payload);
            emailService.sendHtml(payload.getEmail(), subject, html);
            log.info("[MQ-Login] 登录邮件发送成功: userId={}, email={}", payload.getUserId(), payload.getEmail());
        } catch (Exception e) {
            log.error("[MQ-Login] 邮件发送失败: userId={}, error={}", payload.getUserId(), e.getMessage(), e);
            // 邮件发送失败不影响消息消费，消息已被 RabbitMQ ack
            // 如需重试，可结合本地消息表 + 定时任务补偿
        }
    }

    /**
     * 构建登录成功邮件 HTML 内容
     */
    private String buildLoginEmailHtml(LoginEventPayload payload) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>登录成功通知</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <!-- Header -->
                        <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">Delivery 登录通知</h1>
                        </div>
                        
                        <!-- Content -->
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">
                                您好，<strong>%s</strong>：
                            </p>
                            <p style="color: #666666; font-size: 15px; line-height: 1.8;">
                                您的账号于 <strong>%s</strong> 在以下设备登录：
                            </p>
                            
                            <!-- Info Card -->
                            <div style="background: #f8f9fc; border-radius: 8px; padding: 20px; margin: 25px 0;">
                                <table style="width: 100%%; border-collapse: collapse;">
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">登录时间</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                    <tr>
                                        <td style="color: #999999; font-size: 14px; padding: 8px 0;">登录 IP</td>
                                        <td style="color: #333333; font-size: 14px; text-align: right; padding: 8px 0;">%s</td>
                                    </tr>
                                </table>
                            </div>
                            
                            <p style="color: #999999; font-size: 13px; line-height: 1.6;">
                                如果这不是您本人的操作，您的账号可能存在安全风险，请立即修改密码或联系客服。
                            </p>
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
                payload.getLoginTime() != null ? payload.getLoginTime().format(DT_FORMATTER) : "未知",
                payload.getLoginTime() != null ? payload.getLoginTime().format(DT_FORMATTER) : "未知",
                payload.getIp() != null ? escapeHtml(payload.getIp()) : "未知"
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