package com.sakana.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnowflakeIdGenerator 单元测试
 *
 * <p>覆盖：构造参数校验、ID 唯一性、单调递增、批量生成、并发安全。
 */
class SnowflakeIdGeneratorTest {

    private static final long EPOCH = 1577836800000L; // 2020-01-01

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SnowflakeIdGenerator(1L, 1L, EPOCH);
    }

    // ==================== 构造参数校验 ====================

    @Test
    void constructor_validParams_ok() {
        // 不抛异常
        assertDoesNotThrow(() -> new SnowflakeIdGenerator(0L, 0L, EPOCH));
        assertDoesNotThrow(() -> new SnowflakeIdGenerator(31L, 31L, EPOCH));  // 5bit 最大值
    }

    @Test
    void constructor_invalidWorkerId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(-1L, 0L, EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(32L, 0L, EPOCH));  // > 31
    }

    @Test
    void constructor_invalidDatacenterId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(0L, -1L, EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(0L, 32L, EPOCH));
    }

    // ==================== 基础属性 ====================

    @Test
    void nextId_positive() {
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void nextId_monotonic() {
        long prev = generator.nextId();
        for (int i = 0; i < 100; i++) {
            long curr = generator.nextId();
            assertTrue(curr > prev, "ID 必须单调递增");
            prev = curr;
        }
    }

    @Test
    void nextId_uniqueInSequence() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.add(generator.nextId()), "第 " + i + " 个 ID 重复");
        }
    }

    @Test
    void nextId_differentWorkerIds_distinctRanges() {
        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(1L, 1L, EPOCH);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(2L, 1L, EPOCH);

        // 在同一毫秒内，不同 workerId 生成的 ID 不同
        long id1 = gen1.nextId();
        long id2 = gen2.nextId();
        assertNotEquals(id1, id2);
    }

    // ==================== 批量 ====================

    @Test
    void nextIdBatch_correctSize() {
        List<Long> ids = generator.nextIdBatch(100);
        assertEquals(100, ids.size());
    }

    @Test
    void nextIdBatch_allUnique() {
        List<Long> ids = generator.nextIdBatch(500);
        assertEquals(500, new HashSet<>(ids).size(), "批量生成的 ID 应全部唯一");
    }

    @Test
    void nextIdBatch_allMonotonic() {
        List<Long> ids = generator.nextIdBatch(100);
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "批量 ID 应单调递增");
        }
    }

    @Test
    void nextIdBatch_zeroOrNegative_throws() {
        assertThrows(IllegalArgumentException.class, () -> generator.nextIdBatch(0));
        assertThrows(IllegalArgumentException.class, () -> generator.nextIdBatch(-1));
    }

    @Test
    void nextIdBatch_exceedsMax_truncated() {
        // 超过 1000 会被截断为 1000
        List<Long> ids = generator.nextIdBatch(5000);
        assertEquals(1000, ids.size());
    }

    // ==================== 并发 ====================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void nextId_concurrentGeneration_allUnique() throws InterruptedException, ExecutionException {
        int threadCount = 8;
        int idsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        List<Future<Set<Long>>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                Set<Long> localIds = ConcurrentHashMap.newKeySet();
                start.await();
                for (int i = 0; i < idsPerThread; i++) {
                    localIds.add(generator.nextId());
                }
                return localIds;
            }));
        }
        start.countDown();

        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        for (Future<Set<Long>> f : futures) {
            allIds.addAll(f.get());
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(0, errors.get());
        assertEquals(threadCount * idsPerThread, allIds.size(), "并发下所有 ID 应唯一");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void nextId_concurrentGeneration_monotonicPerThread() throws InterruptedException, ExecutionException {
        int threadCount = 4;
        int idsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new java.util.ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                start.await();
                long prev = generator.nextId();
                for (int i = 1; i < idsPerThread; i++) {
                    long curr = generator.nextId();
                    if (curr <= prev) return false;
                    prev = curr;
                }
                return true;
            }));
        }
        start.countDown();

        for (Future<Boolean> f : futures) {
            assertTrue(f.get());
        }
        executor.shutdown();
    }
}
