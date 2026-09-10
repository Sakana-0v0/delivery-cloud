package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * del-message 侧的 RabbitMQ 拓扑配置。
 * <p>
 * 声明四个队列：
 * - del-message.user.event: 绑定到 user.event.exchange，接收用户事件（登录、注册）
 * - del-message.order.event: 绑定到 order.event.exchange，接收订单创建事件
 * - del-message.order.paid: 绑定到 payment.order.paid.exchange，接收订单支付成功事件
 * - del-message.email.send: 绑定到 user.message.exchange (routingKey=email.send)，接收邮件发送事件
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

    /** 用户消息 topic 交换机（与 del-user 保持一致） */
    public static final String USER_MESSAGE_EXCHANGE = "user.message.exchange";

    // ==================== Queue 常量 ====================

    /** del-message 专用的用户事件队列 */
    public static final String USER_EVENT_QUEUE = "del-message.user.event";

    /** del-message 专用的订单创建事件队列 */
    public static final String ORDER_EVENT_QUEUE = "del-message.order.event";

    /** del-message 专用的订单支付成功事件队列 */
    public static final String ORDER_PAID_QUEUE = "del-message.order.paid";

    /** del-message 专用的邮件发送事件队列 */
    public static final String EMAIL_SEND_QUEUE = "del-message.email.send";

    // ==================== MessageConverter ====================

    @Bean
    public MessageConverter userMessageConverter() {
        // PAY-004: 与 del-order 的 Jackson2JsonMessageConverter 对称
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, MessageConverter mc) {
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

    // ==================== 邮件发送事件相关 ====================

    /**
     * 用户消息 topic 交换机
     * <p>
     * del-user 发布邮件事件，本服务订阅消费。
     * routing key = "email.send"
     */
    @Bean
    public TopicExchange userMessageExchange() {
        return new TopicExchange(USER_MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailSendQueue() {
        return new Queue(EMAIL_SEND_QUEUE, true);
    }

    /**
     * 邮件发送队列绑定到 user.message.exchange
     * 使用 routing key "email.send" 精确匹配
     */
    @Bean
    public Binding emailSendBinding(Queue emailSendQueue, TopicExchange userMessageExchange) {
        return BindingBuilder.bind(emailSendQueue).to(userMessageExchange).with("email.send");
    }
}