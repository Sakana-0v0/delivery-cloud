package com.sakana.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池（#P3-MONITORING）。
 *
 * <p>为 del-product 的 IndexingServiceImpl#rebuildAllIndexAsync 提供命名、有界线程池，
 * 替代默认的 SimpleAsyncTaskExecutor（无界、不可观测）。
 *
 * <p>线程命名：index-worker-N，便于日志与 Prometheus 指标关联。
 *
 * <p>关键参数：
 * <ul>
 *   <li>corePoolSize=2：重建索引是非高频任务，常驻 2 个 worker</li>
 *   <li>maxPoolSize=4：高负载时可临时扩到 4 个</li>
 *   <li>queueCapacity=10：缓冲短时并发任务</li>
 *   <li>setWaitForTasksToCompleteOnShutdown(true)：优雅停机时等待任务完成（最长 30s）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /** Spring AOP 默认会找名字为 "taskExecutor" 的 Bean；显式定义避免歧义 */
    public static final String DEFAULT_EXECUTOR = "taskExecutor";

    /** 索引重建专用线程池（IndexingServiceImpl.rebuildAllIndexAsync 使用） */
    public static final String INDEX_EXECUTOR = "indexTaskExecutor";

    @Bean(name = DEFAULT_EXECUTOR)
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("del-product-worker-");
        // 拒绝策略：由调用方线程执行（CallerRunsPolicy），避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[AsyncConfig] default executor initialized: core=4, max=16, queue=50");
        return executor;
    }

    /**
     * 索引重建专用 executor：单独配置避免占用默认 executor 配额。
     * IndexingServiceImpl.rebuildAllIndexAsync() 走这里。
     */
    @Bean(name = INDEX_EXECUTOR)
    public Executor indexTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("index-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("[AsyncConfig] indexTaskExecutor initialized: core=2, max=4, queue=10");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("[AsyncConfig] uncaught async error in method={}, params={}",
                method.getName(), params, ex);
    }
}