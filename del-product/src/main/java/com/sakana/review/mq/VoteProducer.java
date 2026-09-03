package com.sakana.review.mq;

import com.sakana.review.configs.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 点赞事件生产者
 *
 * <p>将点赞/踩操作发送到 MQ，由消费者异步处理计数更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteProducer {
    
    private final RabbitTemplate rabbitTemplate;
    
    /**
     * 发送点赞事件
     */
    public void sendLikeEvent(Long userId, Long orderId, Long productId) {
        VoteEvent event = VoteEvent.like(userId, orderId, productId);
        send(event);
    }
    
    /**
     * 发送踩事件
     */
    public void sendBadEvent(Long userId, Long orderId, Long productId) {
        VoteEvent event = VoteEvent.bad(userId, orderId, productId);
        send(event);
    }
    
    /**
     * 发送取消事件
     */
    public void sendCancelEvent(Long userId, Long orderId, Long productId) {
        VoteEvent event = VoteEvent.cancel(userId, orderId, productId);
        send(event);
    }
    
    /**
     * 发送事件到 MQ
     */
    private void send(VoteEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.VOTE_EXCHANGE,
                RabbitMQConfig.VOTE_ROUTING_KEY,
                event
            );
            log.info("[VoteProducer] 发送事件: action={}, userId={}, orderId={}, productId={}",
                    event.getAction(), event.getUserId(), event.getOrderId(), event.getProductId());
        } catch (Exception e) {
            log.error("[VoteProducer] 发送事件失败: {}", event, e);
        }
    }
}