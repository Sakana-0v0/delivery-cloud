package com.sakana.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.configs.RabbitMQConfig;
import com.sakana.dao.entity.PayOutbox;
import com.sakana.dao.mapper.PayOutboxMapper;
import com.sakana.enums.PayOutboxStatus;
import com.sakana.events.OrderPaidPayload;
import com.sakana.services.impl.PaymentServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pay_outbox 扫描器：定时把状态为 NEW 的行投递到 RabbitMQ，标 SENT。
 * <p>
 * 失败处理：增加 retryCount + 记录 lastError，保留为 NEW 留给下一轮重试。
 * 建议：超过一定阈值（如 10 次）则标记 DLQ，由运维介入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOutboxRelay {

    private final PayOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentServiceImpl paymentServiceImpl;

    @Value("${app.outbox.relay-enabled:true}")
    private boolean relayEnabled;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        if (!relayEnabled) {
            return;
        }
        List<PayOutbox> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<PayOutbox>()
                        .eq(PayOutbox::getStatus, PayOutboxStatus.NEW.code())
                        .last("LIMIT " + batchSize));
        if (pending.isEmpty()) {
            return;
        }
        log.info("[outbox-relay] 本轮扫描 {} 条", pending.size());

        for (PayOutbox o : pending) {
            try {
                OrderPaidPayload payload = paymentServiceImpl.buildPayload(o);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.PAID_EXCHANGE,
                        null,                       // fanout 不需要 routing key
                        payload);
                o.setStatus(PayOutboxStatus.SENT.code());
                outboxMapper.updateById(o);
                log.info("[outbox-relay] eventId={} orderNo={} -> MQ 已投递",
                        o.getEventId(), o.getOrderNo());
            } catch (Exception e) {
                int retry = (o.getRetryCount() == null ? 0 : o.getRetryCount()) + 1;
                o.setRetryCount(retry);
                o.setLastError(e.toString());
                outboxMapper.updateById(o);
                log.error("[outbox-relay] 投递失败 eventId={} retry={} err={}",
                        o.getEventId(), retry, e.toString());
            }
        }
    }
}
