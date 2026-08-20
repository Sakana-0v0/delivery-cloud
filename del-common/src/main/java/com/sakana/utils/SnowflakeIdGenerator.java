package com.sakana.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 雪花算法 ID 生成器（本地版本）
 *
 * <p>从 del-IdGenerator 服务下沉而来。
 * 每个服务实例通过 workerId 区分，生成全局唯一 Long ID。
 *
 * <p>ID 结构：[ 1bit | 41bit时间戳 | 5bit datacenter | 5bit worker | 12bit序列号 ]
 */
public class SnowflakeIdGenerator {

    private final long workerId;
    private final long datacenterId;
    private final long epoch;

    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long sequenceBits = 12L;
    private final long maxWorkerId = ~(-1L << workerIdBits);
    private final long maxDatacenterId = ~(-1L << datacenterIdBits);
    private final long sequenceMask = ~(-1L << sequenceBits);

    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    private static final int BATCH_MAX_SIZE = 1000;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long datacenterId, long epoch) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("workerId 范围: 0 ~ " + maxWorkerId);
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 范围: 0 ~ " + maxDatacenterId);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        // 时钟回拨处理
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                try { wait(offset << 1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                timestamp = System.currentTimeMillis();
                if (timestamp < lastTimestamp) {
                    throw new RuntimeException("时钟回拨超过阈值，拒绝生成ID");
                }
            } else {
                throw new RuntimeException("检测到时钟回拨，拒绝生成ID");
            }
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        return ((timestamp - epoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    /**
     * 批量生成 ID（线程安全）
     */
    public List<Long> nextIdBatch(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("批量大小必须大于 0");
        }
        if (size > BATCH_MAX_SIZE) {
            size = BATCH_MAX_SIZE;
        }
        List<Long> idList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            idList.add(nextId());
        }
        return idList;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
