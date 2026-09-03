package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * del-user 侧的 RabbitMQ 拓扑。
 * <p>
 * Exchange {@code user.event.exchange}（fanout）：本服务发布；
 * Exchange {@code user.message.exchange}（topic）：本服务发布邮件消息，供 del-message 消费。
 */
@Configuration
public class RabbitMQConfig {

    /** 用户事件 fanout 交换机 */
    public static final String USER_EVENT_EXCHANGE = "user.event.exchange";

    /** 用户消息 topic 交换机（邮件等） */
    public static final String USER_MESSAGE_EXCHANGE = "user.message.exchange";

    @Bean
    public MessageConverter userMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        // 允许反序列化 del-common.events 包下的所有事件
        converter.addAllowedListPatterns("com.sakana.events.*");
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, MessageConverter mc) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(mc);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }

    @Bean
    public FanoutExchange userEventExchange() {
        return new FanoutExchange(USER_EVENT_EXCHANGE, true, false);
    }

    /**
     * 用户消息 topic 交换机
     * <p>
     * del-user 发布邮件事件，del-message 订阅消费。
     * routing key = "email.send"
     */
    @Bean
    public TopicExchange userMessageExchange() {
        return new TopicExchange(USER_MESSAGE_EXCHANGE, true, false);
    }
}
