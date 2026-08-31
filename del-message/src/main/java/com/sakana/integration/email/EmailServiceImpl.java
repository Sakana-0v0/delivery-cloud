package com.sakana.integration.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 邮件服务实现（基于 Spring Boot Mail）
 *
 * <p>配置在 Nacos（namespace=sakana，data-id=del-message.yml）：
 * <pre>
 * spring.mail.host=smtp.163.com
 * spring.mail.port=465
 * spring.mail.username=xxx@163.com
 * spring.mail.password=授权码
 * spring.mail.properties.mail.smtp.ssl.enable=true
 * </pre>
 *
 * <p>本地开发时可通过设置 {@code app.email.enabled=false} 禁用邮件发送，
 * 邮件发送会改为只记录日志，不影响 MQ 消息消费功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@sakana.com}")
    private String from;

    @Override
    public void send(String to, String subject, String content) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(content);
            mailSender.send(msg);
            log.info("[邮件发送] to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("[邮件发送] 失败 to={}, subject={}: {}", to, subject, e.getMessage(), e);
        }
    }

    @Override
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("[邮件发送-HTML] to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("[邮件发送-HTML] 失败 to={}, subject={}: {}", to, subject, e.getMessage(), e);
        }
    }
}
