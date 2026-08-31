package com.sakana.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户注册成功事件。
 * <p>
 * 由 del-user 写 user_event_outbox，再由 UserEventOutboxRelay 投递到
 * RabbitMQ fanout exchange <code>user.event.exchange</code>。
 * <p>
 * 消费方：del-message（发送注册欢迎邮件）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterEventPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一ID（消费端幂等键） */
    private String eventId;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 用户邮箱 */
    private String email;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 事件发布时间 */
    private LocalDateTime occurredAt;
}
