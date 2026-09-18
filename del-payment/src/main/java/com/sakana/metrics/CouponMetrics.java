package com.sakana.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 免单券业务指标（#P3-MONITORING）。
 *
 * <p>对接 Micrometer 暴露 /actuator/prometheus。
 * <ul>
 *   <li>coupon.grab.attempts{outcome}：抢券结果分布（success/already_grabbed/quota/duplicate）</li>
 *   <li>coupon.grab.latency：单次抢券端到端延迟</li>
 *   <li>coupon.warmup.duration：发布活动时批量预热总耗时</li>
 *   <li>coupon.warmup.batches：批量预热的批次数</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CouponMetrics {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ALREADY_GRABBED = "already_grabbed";
    public static final String OUTCOME_QUOTA_EXHAUSTED = "quota_exhausted";
    public static final String OUTCOME_DUPLICATE_KEY = "duplicate_key";
    public static final String OUTCOME_NOT_AVAILABLE = "not_available";
    public static final String OUTCOME_NOT_FOUND = "not_found";

    private final MeterRegistry registry;

    private Counter grabAttempts(String outcome) {
        return Counter.builder("coupon.grab.attempts")
                .tag("outcome", outcome)
                .description("抢免单请求结果")
                .register(registry);
    }

    private Timer grabLatency() {
        return Timer.builder("coupon.grab.latency")
                .description("抢免单端到端延迟")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private Timer warmupTimer() {
        return Timer.builder("coupon.warmup.duration")
                .description("发布免单活动批量预热总耗时")
                .register(registry);
    }

    private Counter warmupBatches() {
        return Counter.builder("coupon.warmup.batches")
                .description("批量预热的 SQL 批次数")
                .register(registry);
    }

    public void recordGrab(String outcome, long elapsedMs) {
        grabAttempts(outcome).increment();
        grabLatency().record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    public void recordWarmup(long elapsedMs, int batchCount) {
        warmupTimer().record(elapsedMs, TimeUnit.MILLISECONDS);
        warmupBatches().increment(batchCount);
    }
}
