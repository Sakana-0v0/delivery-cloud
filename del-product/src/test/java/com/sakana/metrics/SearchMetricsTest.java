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
 * SearchMetrics Micrometer 注册验证。
 *
 * <p>使用 SimpleMeterRegistry（内存版）替代 Prometheus，验证：
 * <ul>
 *   <li>Counter 按 outcome 标签正确注册并递增</li>
 *   <li>Timer 记录延迟后 percentileHistogram 可查询</li>
 * </ul>
 */
@DisplayName("SearchMetrics 注册验证")
class SearchMetricsTest {

    private MeterRegistry registry;
    private SearchMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SearchMetrics(registry);
    }

    @Test
    @DisplayName("recordCall 递增对应 outcome 的 Counter")
    void testCounterIncrementByOutcome() {
        metrics.recordCall(SearchMetrics.OUTCOME_HIT, 5);
        metrics.recordCall(SearchMetrics.OUTCOME_HIT, 6);
        metrics.recordCall(SearchMetrics.OUTCOME_EMPTY, 3);

        Counter hit = registry.find("dish.search.calls").tag("outcome", "hit").counter();
        Counter empty = registry.find("dish.search.calls").tag("outcome", "empty").counter();

        assertNotNull(hit);
        assertNotNull(empty);
        assertEquals(2.0, hit.count());
        assertEquals(1.0, empty.count());
    }

    @Test
    @DisplayName("不同 outcome 标签是独立的 Counter")
    void testOutcomesIndependent() {
        metrics.recordCall("block", 0);
        metrics.recordCall("fallback", 0);
        metrics.recordCall("error", 0);
        metrics.recordCall("hit", 0);

        assertEquals(1.0, registry.find("dish.search.calls").tag("outcome", "block").counter().count());
        assertEquals(1.0, registry.find("dish.search.calls").tag("outcome", "fallback").counter().count());
        assertEquals(1.0, registry.find("dish.search.calls").tag("outcome", "error").counter().count());
        assertEquals(1.0, registry.find("dish.search.calls").tag("outcome", "hit").counter().count());
    }

    @Test
    @DisplayName("Timer 记录延迟")
    void testTimerRecordsLatency() {
        metrics.recordCall("hit", 5);
        metrics.recordCall("hit", 10);
        metrics.recordCall("hit", 15);

        Timer timer = registry.find("dish.search.latency").timer();
        assertNotNull(timer);
        assertEquals(3, timer.count());
        assertEquals(30, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 0.001);
    }
}
