package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sakana.dao.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_message")
public class Message extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 消息类型：order/pay/refund/system
     */
    private String type;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 是否已读（0未读，1已读）
     */
    private Integer isRead;

    /**
     * 关联业务ID（订单号/支付单号等）
     */
    private String bizId;
}
