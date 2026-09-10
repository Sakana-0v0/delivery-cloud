package com.sakana.review.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 评价投票事件消息（#PROD-VOTE-010）
 *
 * <p>vote_persist 事件由 VotePersistConsumer 处理，异步落 t_review 表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件唯一ID（用于 Consumer 幂等去重，#PROD-VOTE-007） */
    private String eventId;

    /** 事件类型：vote_persist */
    private String action;

    /**
     * 目标状态（vote_persist 事件专用）
     * like / bad / null（null 表示取消）
     */
    private String targetType;

    /**
     * 动作前状态（vote_persist 事件专用）
     * like / bad / null（null 表示之前无记录）
     */
    private String oldType;

    /** 用户ID */
    private Long userId;

    /** 订单ID */
    private Long orderId;

    /** 商品ID */
    private Long productId;

    /** 事件时间 */
    private LocalDateTime eventTime;

    /**
     * 生成事件唯一ID
     */
    public static String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构造 vote_persist 事件
     */
    public static VoteEvent votePersist(Long userId, Long orderId, Long productId,
                                         String targetType, String oldType) {
        return new VoteEvent(
                generateEventId(),
                "vote_persist",
                targetType,
                oldType,
                userId,
                orderId,
                productId,
                LocalDateTime.now()
        );
    }
}
