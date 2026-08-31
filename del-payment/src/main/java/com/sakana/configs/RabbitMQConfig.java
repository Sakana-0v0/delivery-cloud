package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * del-payment 侧的 RabbitMQ 拓扑。
 * <p>
 * Exchange {@code payment.order.paid.exchange}（fanout）：本服务发布；
 * 各业务服务订阅自己的 queue（不在本服务声明）。
 * DLX/DLQ：消费失败的消息统一汇入 {@code payment.order.paid.dlx.exchange}。
 */
@Configuration
public class RabbitMQConfig {

    /** 支付成功事件（fanout，订单 + 消息两个服务都接收） */
    public static final String PAID_EXCHANGE = "payment.order.paid.exchange";

    /** 死信交换机 */
    public static final String PAID_DLX_EXCHANGE = "payment.order.paid.dlx.exchange";

    @Bean
    public MessageConverter paymentMessageConverter() {
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
    public FanoutExchange paidExchange() {
        return new FanoutExchange(PAID_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange paidDlxExchange() {
        return new FanoutExchange(PAID_DLX_EXCHANGE, true, false);
    }

    /**
     * 声明 DLQ 队列 + binding（供其他服务把失败消息路由到这里）
     */
    @Bean
    public Queue paidDlqQueue() {
        return QueueBuilder.durable("payment.order.paid.dlq").build();
    }

    @Bean
    public Binding paidDlqBinding() {
        return BindingBuilder.bind(paidDlqQueue()).to(paidDlxExchange());
    }
}
