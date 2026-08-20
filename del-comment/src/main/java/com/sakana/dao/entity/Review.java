package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品售后评价实体
 * <p>
 * 评价按订单独立：同一商品在不同订单可分别评价，
 * 由唯一键 (order_id, product_id) 保证同一订单内同一商品只保留一条记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_review")
public class Review extends BaseEntity {

    /**
     * 评价用户ID
     */
    private Long userId;

    /**
     * 所属订单ID
     */
    private Long orderId;

    /**
     * 被评商品ID
     */
    private Long productId;

    /**
     * 评价类型：1 赞 2 踩
     */
    private Integer type;
}
