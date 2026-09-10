package com.sakana.review.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价事件日志实体（t_review_log）
 *
 * <p>记录每一次评价动作的不可变日志，用于：
 * <ul>
 *   <li>管理员面板数据来源（TOP10、趋势、取消率）</li>
 *   <li>Redis 计数丢失后的重建源</li>
 *   <li>审计追溯</li>
 * </ul>
 *
 * <p>此表为 append-only（仅插入，不更新不删除）
 */
@Data
@TableName("t_review_log")
public class ReviewLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 评价用户ID
     */
    private Long userId;

    /**
     * 所属订单ID
     */
    private Long orderId;

    /**
     * 被评价商品ID
     */
    private Long productId;

    /**
     * 动作类型（1=LIKE, 2=BAD, 3=CANCEL_LIKE, 4=CANCEL_BAD, 5=CHANGE_TO_BAD, 6=CHANGE_TO_LIKE）
     * @see com.sakana.review.enums.ReviewAction
     */
    private Integer action;

    /**
     * 动作前的旧状态（1=赞, 2=踩, null=无记录）
     * 用于Consumer重建t_review当前状态
     */
    private Integer oldAction;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 创建时间（入库时间）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
