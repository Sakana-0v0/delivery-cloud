package com.sakana.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CouponMetrics 注册验证。
 */
@DisplayName("CouponMetrics 注册验证")
class CouponMetricsTest {

    private MeterRegistry registry;
    private CouponMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new CouponMetrics(registry);
    }

    @Test
    @DisplayName("recordGrab 按 outcome 区分")
    void testGrabCounter() {
        metrics.recordGrab(CouponMetrics.OUTCOME_SUCCESS, 25);
        metrics.recordGrab(CouponMetrics.OUTCOME_SUCCESS, 35);
        metrics.recordGrab(CouponMetrics.OUTCOME_ALREADY_GRABBED, 5);
        metrics.recordGrab(CouponMetrics.OUTCOME_QUOTA_EXHAUSTED, 8);

        assertEquals(2.0, registry.find("coupon.grab.attempts").tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.find("coupon.grab.attempts").tag("outcome", "already_grabbed").counter().count());
        assertEquals(1.0, registry.find("coupon.grab.attempts").tag("outcome", "quota_exhausted").counter().count());
    }

    @Test
    @DisplayName("grab Latency Timer")
    void testGrabTimer() {
        metrics.recordGrab("success", 10);
        metrics.recordGrab("success", 20);
        metrics.recordGrab("success", 30);

        Timer timer = registry.find("coupon.grab.latency").timer();
        assertNotNull(timer);
        assertEquals(3, timer.count());
        assertEquals(60, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 0.001);
    }

    @Test
    @DisplayName("recordWarmup 上报总耗时 + 批次数")
    void testWarmupMetrics() {
        metrics.recordWarmup(1200, 2); // 1000 张券 / 1.2s / 2 批

        assertEquals(2.0, registry.find("coupon.warmup.batches").counter().count());
        Timer warmupTimer = registry.find("coupon.warmup.duration").timer();
        assertNotNull(warmupTimer);
        assertEquals(1, warmupTimer.count());
        assertEquals(1.2, warmupTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.001);
    }
}
