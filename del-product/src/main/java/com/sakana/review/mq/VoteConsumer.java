package com.sakana.review.mq;

import com.sakana.review.configs.RabbitMQConfig;
import com.sakana.review.services.ReviewCountCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 点赞事件消费者
 *
 * <p>监听 MQ 消息，异步处理计数更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteConsumer {
    
    private final ReviewCountCacheService reviewCountCacheService;
    
    /**
     * 处理点赞/踩事件
     */
    @RabbitListener(queues = RabbitMQConfig.VOTE_QUEUE)
    public void handleVoteEvent(VoteEvent event) {
        if (event == null) {
            log.warn("[VoteConsumer] 收到空事件，跳过");
            return;
        }
        
        log.info("[VoteConsumer] 收到事件: action={}, userId={}, orderId={}, productId={}",
                event.getAction(), event.getUserId(), event.getOrderId(), event.getProductId());
        
        try {
            switch (event.getAction()) {
                case "like" -> handleLike(event);
                case "bad" -> handleBad(event);
                case "cancel" -> handleCancel(event);
                default -> log.warn("[VoteConsumer] 未知操作类型: {}", event.getAction());
            }
        } catch (Exception e) {
            log.error("[VoteConsumer] 处理事件失败: {}", event, e);
        }
    }
    
    /**
     * 处理点赞
     */
    private void handleLike(VoteEvent event) {
        reviewCountCacheService.incrementCount(event.getProductId(), 1, 0);
        reviewCountCacheService.invalidateLocal(event.getProductId());
        log.info("[VoteConsumer] 点赞处理完成: productId={}", event.getProductId());
    }
    
    /**
     * 处理踩
     */
    private void handleBad(VoteEvent event) {
        reviewCountCacheService.incrementCount(event.getProductId(), 0, 1);
        reviewCountCacheService.invalidateLocal(event.getProductId());
        log.info("[VoteConsumer] 踩处理完成: productId={}", event.getProductId());
    }
    
    /**
     * 处理取消（需要根据之前的状态决定减少哪个计数）
     * 注意：取消事件需要知道之前投的是什么，这里简化处理
     * 实际生产中可以通过查询或传递更多信息来确定
     */
    private void handleCancel(VoteEvent event) {
        // 取消操作时，只使缓存失效，让下次查询时从数据库重新加载
        reviewCountCacheService.invalidateLocal(event.getProductId());
        log.info("[VoteConsumer] 取消处理完成（缓存失效）: productId={}", event.getProductId());
    }
}