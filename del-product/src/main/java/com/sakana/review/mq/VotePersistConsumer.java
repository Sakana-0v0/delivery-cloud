package com.sakana.review.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.review.dao.entity.Review;
import com.sakana.review.dao.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 投票持久化消费者（#PROD-VOTE-010）
 *
 * <p>监听 vote_persist 事件，异步将投票结果写入 t_review 表。
 * <p>幂等保证：
 * <ul>
 *   <li>SETNX 检查 eventId（防止同一事件重复处理）</li>
 *   <li>t_review 唯一键 (order_id, product_id, is_deleted=0) 保证同一订单-商品只有一条有效记录</li>
 * </ul>
 *
 * <p>注意：此 Consumer 与 VoteConsumer 共用同一个队列（VOTE_QUEUE），
 * 但通过 action='vote_persist' 分发到本 Consumer 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VotePersistConsumer {

    private final ReviewMapper reviewMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PROCESSED_KEY_PREFIX = "vote:persist:processed:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    /**
     * 处理投票持久化事件
     *
     * <p>根据 targetType 和 oldType 重建 t_review 当前状态：
     * <pre>
     * oldType=null,  targetType=like  → INSERT (type=1)
     * oldType=null,  targetType=bad   → INSERT (type=2)
     * oldType=like,  targetType=like  → DELETE（toggle取消）
     * oldType=bad,   targetType=bad   → DELETE（toggle取消）
     * oldType=like,  targetType=bad    → UPDATE (type=2)
     * oldType=bad,   targetType=like  → UPDATE (type=1)
     * oldType=like,  targetType=null  → DELETE（显式取消赞）
     * oldType=bad,   targetType=null  → DELETE（显式取消踩）
     * </pre>
     */
    @RabbitListener(queues = "vote.queue")
    public void handleVotePersistEvent(VoteEvent event) {
        // 只处理 vote_persist 类型，其他类型（like/bad/cancel）由 VoteConsumer 处理
        if (event == null || !"vote_persist".equals(event.getAction())) {
            return;
        }

        if (event.getEventId() == null) {
            log.warn("[VotePersistConsumer] eventId 为空，跳过: userId={}, productId={}",
                    event.getUserId(), event.getProductId());
            return;
        }

        // 1. 幂等去重检查
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();
        Boolean firstTime;
        try {
            firstTime = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", PROCESSED_TTL);
        } catch (Exception e) {
            log.warn("[VotePersistConsumer] Redis SETNX 失败，降级处理: eventId={}, error={}",
                    event.getEventId(), e.getMessage());
            firstTime = true;
        }

        if (Boolean.FALSE.equals(firstTime)) {
            log.info("[VotePersistConsumer] 重复消息，跳过: eventId={}", event.getEventId());
            return;
        }

        log.info("[VotePersistConsumer] 处理vote_persist: eventId={}, userId={}, orderId={}, productId={}, oldType={}, targetType={}",
                event.getEventId(), event.getUserId(), event.getOrderId(), event.getProductId(),
                event.getOldType(), event.getTargetType());

        try {
            doPersist(event);
        } catch (Exception e) {
            // 处理失败，删除幂等标记，允许重试
            try {
                stringRedisTemplate.delete(dedupKey);
            } catch (Exception ignored) {
            }
            log.error("[VotePersistConsumer] 持久化失败，已回滚幂等标记: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * 执行持久化逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public void doPersist(VoteEvent event) {
        Long userId = event.getUserId();
        Long orderId = event.getOrderId();
        Long productId = event.getProductId();
        String targetType = event.getTargetType();
        String oldType = event.getOldType();

        // 查询当前 DB 状态（用于幂等判断）
        Review existing = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getProductId, productId)
                        .eq(Review::getIsDeleted, 0));

        String currentType = existing == null ? null : (existing.getType() == 1 ? "like" : "bad");

        // 根据状态机执行 DB 操作
        if (targetType == null) {
            // ── 取消操作：DELETE ──────────────────────────
            if (existing != null) {
                reviewMapper.physicalDeleteById(existing.getId());
                log.info("[VotePersistConsumer] DELETE t_review: userId={}, productId={}", userId, productId);
            }
            // 无记录则是 no-op

        } else if (oldType == null) {
            // ── 新增投票：INSERT ─────────────────────────
            // 幂等：如果当前已有记录（可能并发导致），跳过
            if (existing == null) {
                Review r = new Review();
                r.setUserId(userId);
                r.setOrderId(orderId);
                r.setProductId(productId);
                r.setType("like".equals(targetType) ? 1 : 2);
                reviewMapper.insert(r);
                log.info("[VotePersistConsumer] INSERT t_review: userId={}, productId={}, type={}",
                        userId, productId, targetType);
            } else {
                log.info("[VotePersistConsumer] INSERT 跳过（记录已存在）: userId={}, productId={}",
                        userId, productId);
            }

        } else if (oldType.equals(targetType)) {
            // ── Toggle 取消：DELETE ─────────────────────
            if (existing != null) {
                reviewMapper.physicalDeleteById(existing.getId());
                log.info("[VotePersistConsumer] TOGGLE-DELETE t_review: userId={}, productId={}, oldType={}",
                        userId, productId, oldType);
            }

        } else {
            // ── 切换投票：UPDATE ───────────────────────
            // 幂等：如果当前状态已经是 targetType，跳过
            if (existing != null) {
                if (currentType != null && currentType.equals(targetType)) {
                    log.info("[VotePersistConsumer] UPDATE 跳过（状态已一致）: userId={}, productId={}, currentType={}",
                            userId, productId, currentType);
                } else {
                    Review r = new Review();
                    r.setId(existing.getId());
                    r.setType("like".equals(targetType) ? 1 : 2);
                    reviewMapper.updateById(r);
                    log.info("[VotePersistConsumer] UPDATE t_review: userId={}, productId={}, {}→{}",
                            userId, productId, oldType, targetType);
                }
            } else {
                // 记录不存在（可能被其他事件删除了），重新 INSERT
                Review r = new Review();
                r.setUserId(userId);
                r.setOrderId(orderId);
                r.setProductId(productId);
                r.setType("like".equals(targetType) ? 1 : 2);
                reviewMapper.insert(r);
                log.info("[VotePersistConsumer] RE-INSERT t_review: userId={}, productId={}, type={}",
                        userId, productId, targetType);
            }
        }
    }
}
