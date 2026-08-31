package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户事件 outbox（事务性发件箱）。
 *
 * <h2>使用流程</h2>
 * <ol>
 *   <li>UserService 在同一事务里 INSERT t_user + INSERT user_event_outbox（NEW 状态）</li>
 *   <li>UserEventOutboxRelay 定时扫描 NEW 行 → 投递到 RabbitMQ → 标 SENT</li>
 *   <li>消费方手动 ACK / NACK 之后由回执更新 ACK / DLQ（可选）</li>
 * </ol>
 *
 * <h2>为什么用 outbox</h2>
 * "改本地 t_user" 与 "发 MQ 通知其他服务" 无法原子化。
 * outbox 模式保证：业务事务提交 = outbox 一定落库；定时器 = 至少一次投递；消费方 = 幂等处理。
 */
@Data
@TableName("user_event_outbox")
public class UserEventOutbox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一ID（用于消费端幂等） */
    private String eventId;

    /** 事件类型：REGISTER / LOGIN */
    private String eventType;

    /** 关联用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 用户邮箱 */
    private String email;

    /** 登录IP（仅登录事件有值） */
    private String ip;

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
}
