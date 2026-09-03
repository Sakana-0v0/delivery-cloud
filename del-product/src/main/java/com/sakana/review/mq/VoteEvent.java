package com.sakana.review.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞/踩事件消息
 *
 * <p>用于 MQ 异步处理点赞/踩操作的计数更新
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 操作类型：like=点赞, bad=踩, cancel=取消 */
    private String action;
    
    /** 用户ID */
    private Long userId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 商品ID */
    private Long productId;
    
    /** 事件时间 */
    private LocalDateTime eventTime;
    
    /**
     * 构造点赞事件
     */
    public static VoteEvent like(Long userId, Long orderId, Long productId) {
        return new VoteEvent("like", userId, orderId, productId, LocalDateTime.now());
    }
    
    /**
     * 构造踩事件
     */
    public static VoteEvent bad(Long userId, Long orderId, Long productId) {
        return new VoteEvent("bad", userId, orderId, productId, LocalDateTime.now());
    }
    
    /**
     * 构造取消事件
     */
    public static VoteEvent cancel(Long userId, Long orderId, Long productId) {
        return new VoteEvent("cancel", userId, orderId, productId, LocalDateTime.now());
    }
}