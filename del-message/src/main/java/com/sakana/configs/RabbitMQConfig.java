package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * del-message 侧的 RabbitMQ 拓扑配置。
 * <p>
 * 声明三个队列：
 * - del-message.user.event: 绑定到 user.event.exchange，接收用户事件（登录、注册）
 * - del-message.order.event: 绑定到 order.event.exchange，接收订单创建事件
 * - del-message.order.paid: 绑定到 payment.order.paid.exchange，接收订单支付成功事件
 */
@Configuration
public class RabbitMQConfig {

    // ==================== Exchange 常量 ====================

    /** 用户事件 fanout 交换机（与 del-user 保持一致） */
    public static final String USER_EVENT_EXCHANGE = "user.event.exchange";

    /** 订单事件 fanout 交换机（与 del-order 保持一致） */
    public static final String ORDER_EVENT_EXCHANGE = "order.event.exchange";

    /** 支付成功事件 fanout 交换机（与 del-payment 保持一致） */
    public static final String PAID_EXCHANGE = "payment.order.paid.exchange";

    // ==================== Queue 常量 ====================

    /** del-message 专用的用户事件队列 */
    public static final String USER_EVENT_QUEUE = "del-message.user.event";

    /** del-message 专用的订单创建事件队列 */
    public static final String ORDER_EVENT_QUEUE = "del-message.order.event";

    /** del-message 专用的订单支付成功事件队列 */
    public static final String ORDER_PAID_QUEUE = "del-message.order.paid";

    // ==================== MessageConverter ====================

    @Bean
    public SimpleMessageConverter userMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        // 允许反序列化 com.sakana.events 包下的所有事件
        converter.addAllowedListPatterns("com.sakana.events.*");
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, SimpleMessageConverter mc) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(mc);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }

    // ==================== 用户事件相关 ====================

    @Bean
    public FanoutExchange userEventExchange() {
        return new FanoutExchange(USER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue userEventQueue() {
        return new Queue(USER_EVENT_QUEUE, true);
    }

    @Bean
    public Binding userEventBinding(Queue userEventQueue, FanoutExchange userEventExchange) {
        return BindingBuilder.bind(userEventQueue).to(userEventExchange);
    }

    // ==================== 订单事件相关 ====================

    @Bean
    public FanoutExchange orderEventExchange() {
        return new FanoutExchange(ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEventQueue() {
        return new Queue(ORDER_EVENT_QUEUE, true);
    }

    @Bean
    public Binding orderEventBinding(Queue orderEventQueue, FanoutExchange orderEventExchange) {
        return BindingBuilder.bind(orderEventQueue).to(orderEventExchange);
    }

    // ==================== 支付成功事件相关 ====================

    @Bean
    public FanoutExchange paidExchange() {
        return new FanoutExchange(PAID_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, FanoutExchange paidExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(paidExchange);
    }
}