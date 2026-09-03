package com.sakana.review.configs;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 *
 * <p>点赞/踩事件使用 Direct Exchange
 */
@Configuration
public class RabbitMQConfig {
    
    /** 交换机名称 */
    public static final String VOTE_EXCHANGE = "vote.exchange";
    
    /** 队列名称 */
    public static final String VOTE_QUEUE = "vote.queue";
    
    /** 路由键 */
    public static final String VOTE_ROUTING_KEY = "vote.key";
    
    /**
     * 点赞事件交换机
     */
    @Bean
    public DirectExchange voteExchange() {
        return new DirectExchange(VOTE_EXCHANGE, true, false);
    }
    
    /**
     * 点赞事件队列
     */
    @Bean
    public Queue voteQueue() {
        return new Queue(VOTE_QUEUE, true);
    }
    
    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding voteBinding(Queue voteQueue, DirectExchange voteExchange) {
        return BindingBuilder.bind(voteQueue).to(voteExchange).with(VOTE_ROUTING_KEY);
    }
    
    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate 配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}