package com.sakana.integration.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 邮件服务Stub实现（邮件功能禁用时）
 *
 * <p>当 {@code app.email.enabled=false} 时，Spring 会加载此实现代替 {@link EmailServiceImpl}。
 * 邮件发送会改为只记录日志，不影响 MQ 消息消费功能。
 *
 * <p>生产环境请设置 {@code app.email.enabled=true} 并配置真实的邮件服务器。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = false)
public class EmailServiceStub implements EmailService {

    @Override
    public void send(String to, String subject, String content) {
        log.info("[邮件发送-已禁用] to={}, subject={}, content={}", to, subject, content);
    }

    @Override
    public void sendHtml(String to, String subject, String html) {
        log.info("[邮件发送-HTML-已禁用] to={}, subject={}", to, subject);
    }
}
