package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.FanoutExchange;
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
 * 各业务服务订阅自己的 queue（不在本服务声明）。
 */
@Configuration
public class RabbitMQConfig {

    /** 用户事件 fanout 交换机 */
    public static final String USER_EVENT_EXCHANGE = "user.event.exchange";

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
}
