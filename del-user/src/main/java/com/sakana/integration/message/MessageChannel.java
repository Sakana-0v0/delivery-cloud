package com.sakana.integration.message;

import com.sakana.events.EmailMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息通道（真实实现）
 * <p>
 * 将消息投递到 RabbitMQ，由 del-message 异步消费并发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChannel {

    /** 用户消息 topic 交换机 */
    public static final String USER_MESSAGE_EXCHANGE = "user.message.exchange";

    /** email 消息 routing key */
    public static final String EMAIL_ROUTING_KEY = "email.send";

    private final RabbitTemplate rabbitTemplate;

    public void send(String type, String target, String subject,
                     String template, Map<String, Object> params) {
        if ("email".equals(type)) {
            EmailMessageEvent event = new EmailMessageEvent(target, subject, template, params);
            rabbitTemplate.convertAndSend(USER_MESSAGE_EXCHANGE, EMAIL_ROUTING_KEY, event);
            log.info("[MessageChannel] 邮件消息已投递到 MQ: to={}, subject={}, template={}",
                    target, subject, template);
        } else {
            log.warn("[MessageChannel] 暂不支持的消息类型: type={}, target={}", type, target);
        }
    }
}
