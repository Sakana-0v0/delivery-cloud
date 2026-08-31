package com.sakana.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.UserEventOutbox;
import com.sakana.dao.mapper.UserEventOutboxMapper;
import com.sakana.enums.UserOutboxStatus;
import com.sakana.events.LoginEventPayload;
import com.sakana.events.RegisterEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * user_event_outbox 扫描器：定时把状态为 NEW 的行投递到 RabbitMQ，标 SENT。
 * <p>
 * 失败处理：增加 retryCount + 记录 lastError，保留为 NEW 留给下一轮重试。
 * 建议：超过一定阈值（如 10 次）则标记 DLQ，由运维介入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventOutboxRelay {

    /** 用户事件 fanout 交换机 */
    public static final String USER_EVENT_EXCHANGE = "user.event.exchange";

    private final UserEventOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.outbox.relay-enabled:true}")
    private boolean relayEnabled;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        if (!relayEnabled) {
            return;
        }
        List<UserEventOutbox> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<UserEventOutbox>()
                        .eq(UserEventOutbox::getStatus, UserOutboxStatus.NEW.code())
                        .last("LIMIT " + batchSize));
        if (pending.isEmpty()) {
            return;
        }
        log.info("[user-outbox-relay] 本轮扫描 {} 条", pending.size());

        for (UserEventOutbox o : pending) {
            try {
                if ("REGISTER".equals(o.getEventType())) {
                    RegisterEventPayload payload = RegisterEventPayload.builder()
                            .eventId(o.getEventId())
                            .userId(o.getUserId())
                            .username(o.getUsername())
                            .email(o.getEmail())
                            .registerTime(o.getCreateTime())
                            .occurredAt(LocalDateTime.now())
                            .build();
                    rabbitTemplate.convertAndSend(USER_EVENT_EXCHANGE, null, payload);
                } else if ("LOGIN".equals(o.getEventType())) {
                    LoginEventPayload payload = LoginEventPayload.builder()
                            .eventId(o.getEventId())
                            .userId(o.getUserId())
                            .username(o.getUsername())
                            .email(o.getEmail())
                            .loginTime(o.getCreateTime())
                            .ip(o.getIp())
                            .occurredAt(LocalDateTime.now())
                            .build();
                    rabbitTemplate.convertAndSend(USER_EVENT_EXCHANGE, null, payload);
                }
                o.setStatus(UserOutboxStatus.SENT.code());
                outboxMapper.updateById(o);
                log.info("[user-outbox-relay] eventId={} userId={} -> MQ 已投递",
                        o.getEventId(), o.getUserId());
            } catch (Exception e) {
                int retry = (o.getRetryCount() == null ? 0 : o.getRetryCount()) + 1;
                o.setRetryCount(retry);
                o.setLastError(e.toString());
                outboxMapper.updateById(o);
                log.error("[user-outbox-relay] 投递失败 eventId={} retry={} err={}",
                        o.getEventId(), retry, e.toString());
            }
        }
    }

    /**
     * 生成事件ID（供 Service 层在事务内调用）
     */
    public static String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
