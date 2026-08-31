package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付事件 outbox（事务性发件箱）。
 *
 * <h2>使用流程</h2>
 * <ol>
 *   <li>handleNotify 在同一事务里 UPDATE t_payment + INSERT pay_outbox（NEW 状态）</li>
 *   <li>PayOutboxRelay 定时扫描 NEW 行 → 投递到 RabbitMQ → 标 SENT</li>
 *   <li>消费方手动 ACK / NACK 之后由回执更新 ACK / DLQ（可选）</li>
 * </ol>
 *
 * <h2>为什么用 outbox</h2>
 * "改本地 t_payment" 与 "发 MQ 通知其他服务" 无法原子化。
 * outbox 模式保证：业务事务提交 = outbox 一定落库；定时器 = 至少一次投递；消费方 = 幂等处理。
 */
@Data
@TableName("pay_outbox")
public class PayOutbox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一ID（用于消费端幂等） */
    private String eventId;

    /** 关联支付流水号 */
    private String payNo;

    /** 关联订单号 */
    private String orderNo;

    /** 下单用户ID */
    private Long userId;

    /** 用户名（创建支付时已落库，避免回调时再查用户服务） */
    private String username;

    /** 用户邮箱（同上） */
    private String email;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    /** 支付宝交易号 */
    private String tradeNo;

    /** outbox 状态：0 NEW 1 SENT 2 ACK 3 DLQ */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次失败原因 */
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
