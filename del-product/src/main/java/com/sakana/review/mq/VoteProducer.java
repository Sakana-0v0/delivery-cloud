package com.sakana.review.mq;

import com.sakana.review.configs.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 评价投票事件生产者（#PROD-VOTE-010）
 *
 * <p>发送 vote_persist 事件，异步落 t_review 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送投票持久化事件（异步落 t_review）
     *
     * @param userId     评价用户ID
     * @param orderId    所属订单ID
     * @param productId  被评价商品ID
     * @param targetType 目标状态：like / bad / null
     * @param oldType   旧状态：like / bad / null
     */
    public void sendVotePersistEvent(Long userId, Long orderId, Long productId,
                                     String targetType, String oldType) {
        VoteEvent event = VoteEvent.votePersist(userId, orderId, productId, targetType, oldType);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.VOTE_EXCHANGE,
                    RabbitMQConfig.VOTE_ROUTING_KEY,
                    event
            );
            log.info("[VoteProducer] 发送vote_persist: userId={}, orderId={}, productId={}, oldType={}, targetType={}",
                    userId, orderId, productId, oldType, targetType);
        } catch (Exception e) {
            log.error("[VoteProducer] 发送事件失败: {}", event, e);
        }
    }
}
