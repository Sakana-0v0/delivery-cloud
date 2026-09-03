package com.sakana.consumers.event;

import com.sakana.events.EmailMessageEvent;
import com.sakana.integration.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮件消息事件消费者
 * <p>
 * 监听用户消息邮件事件，调用 EmailService 发送邮件。
 * <p>
 * 事件来源：del-user -> MessageChannel -> user.message.exchange (topic, routingKey=email.send)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailMessageConsumer {

    private final EmailService emailService;

    /** 验证码模板 */
    private static final String TEMPLATE_VERIFY_CODE = "verify-code";

    /**
     * 监听邮件发送事件
     */
    @RabbitListener(queues = "${app.rabbit.email-send-queue:del-message.email.send}")
    public void onEmailMessage(EmailMessageEvent event) {
        log.info("[MQ-Email] 收到邮件事件: to={}, subject={}, template={}, params={}",
                event.getTo(), event.getSubject(), event.getTemplate(), event.getParams());

        if (event.getTo() == null || event.getTo().isBlank()) {
            log.warn("[MQ-Email] 目标邮箱为空，跳过发送");
            return;
        }

        try {
            String html = buildEmailHtml(event);
            emailService.sendHtml(event.getTo(), event.getSubject(), html);
            log.info("[MQ-Email] 邮件发送成功: to={}, template={}", event.getTo(), event.getTemplate());
        } catch (Exception e) {
            log.error("[MQ-Email] 邮件发送失败: to={}, error={}", event.getTo(), e.getMessage(), e);
        }
    }

    /**
     * 根据模板构建邮件 HTML 内容
     */
    private String buildEmailHtml(EmailMessageEvent event) {
        String template = event.getTemplate();
        Map<String, Object> params = event.getParams();

        if (TEMPLATE_VERIFY_CODE.equals(template)) {
            return buildVerifyCodeEmailHtml(params);
        }

        // 默认简单文本邮件
        return buildDefaultEmailHtml(params);
    }

    /**
     * 构建验证码邮件 HTML
     */
    private String buildVerifyCodeEmailHtml(Map<String, Object> params) {
        String code = params != null && params.get("code") != null ? params.get("code").toString() : "未知";
        String minutes = params != null && params.get("minutes") != null ? params.get("minutes").toString() : "5";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>验证码</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <!-- Header -->
                        <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px 30px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 600;">验证码</h1>
                        </div>
                        
                        <!-- Content -->
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">
                                您好，您的验证码是：
                            </p>
                            
                            <!-- Code Card -->
                            <div style="background: #f8f9fc; border-radius: 8px; padding: 30px; margin: 25px 0; text-align: center;">
                                <span style="font-size: 36px; font-weight: bold; color: #667eea; letter-spacing: 8px;">%s</span>
                            </div>
                            
                            <p style="color: #666666; font-size: 14px; line-height: 1.8;">
                                验证码 %s 分钟内有效，请及时完成验证。<br>
                                如非本人操作，请忽略此邮件。
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
                """.formatted(escapeHtml(code), escapeHtml(minutes));
    }

    /**
     * 构建默认邮件 HTML
     */
    private String buildDefaultEmailHtml(Map<String, Object> params) {
        StringBuilder content = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                content.append(entry.getKey()).append(": ").append(entry.getValue()).append("<br>");
            }
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>消息通知</title>
                </head>
                <body style="font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <div style="padding: 40px 30px;">
                            <p style="color: #333333; font-size: 16px; line-height: 1.8;">%s</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(content.length() > 0 ? content.toString() : "您有一条新消息，请查收。");
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