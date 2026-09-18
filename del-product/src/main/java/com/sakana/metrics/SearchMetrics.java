package com.sakana.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 搜索模块业务指标（#P3-MONITORING）。
 *
 * <p>对接 Micrometer，JVM 自动暴露到 /actuator/prometheus。
 * 标签用 outcome 区分成功/降级/熔断/异常，PromQL 可按 outcome 求和算占比。
 *
 * <p>指标名约定：{module}.{action}.{event}，单位：_total（Counter）、_seconds（Timer）。
 */
@Component
@RequiredArgsConstructor
public class SearchMetrics {

    public static final String OUTCOME_HIT = "hit";
    public static final String OUTCOME_EMPTY = "empty";
    public static final String OUTCOME_FALLBACK = "fallback";
    public static final String OUTCOME_BLOCK = "block";
    public static final String OUTCOME_ERROR = "error";

    private final MeterRegistry registry;

    private Counter calls(String outcome) {
        return Counter.builder("dish.search.calls")
                .tag("outcome", outcome)
                .description("菜品搜索调用结果")
                .register(registry);
    }

    private Timer latency() {
        return Timer.builder("dish.search.latency")
                .description("菜品搜索端到端延迟")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordCall(String outcome, long elapsedMs) {
        calls(outcome).increment();
        latency().record(elapsedMs, TimeUnit.MILLISECONDS);
    }
}
