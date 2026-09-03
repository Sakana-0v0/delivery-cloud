package com.sakana.configs;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * del-order 侧的 RabbitMQ 拓扑。
 * <p>
 * 接收 del-payment 发出的 payment.order.paid.exchange（fanout） → 把订单状态改为 PAID。
 * 发布 order.event.exchange（fanout） → 通知其他服务订单创建成功。
 */
@Configuration
public class OrderRabbitMQConfig {

    /** 支付成功事件 exchange（fanout，del-payment 发布） */
    public static final String PAID_EXCHANGE = "payment.order.paid.exchange";

    /** 死信交换机（失败兜底） */
    public static final String PAID_DLX_EXCHANGE = "payment.order.paid.dlx.exchange";

    /** del-order 监听队列 */
    public static final String ORDER_PAID_QUEUE = "del-order.order.paid.queue";

    /** 订单事件 fanout 交换机（本服务发布） */
    public static final String ORDER_EVENT_EXCHANGE = "order.event.exchange";

    /**
     * 主消息转换器（@Primary 解决与 del-product 的 RabbitMQConfig 冲突）
     */
    @Bean
    @Primary
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }

    @Bean
    public FanoutExchange paidExchange() {
        return new FanoutExchange(PAID_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", PAID_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(paidExchange());
    }

    @Bean
    public FanoutExchange orderEventExchange() {
        return new FanoutExchange(ORDER_EVENT_EXCHANGE, true, false);
    }
}