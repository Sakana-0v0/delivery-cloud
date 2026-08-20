package com.sakana.service;


import java.util.ArrayList;
import java.util.List;

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
    private static final int BATCH_MAX_SIZE = 1000;  // 可根据实际需求调整
    //序列号
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    //传入workerId(根据IP动态生成)
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
        //获取当前毫秒时间戳
        long timestamp = System.currentTimeMillis();
        // 时钟回拨处理（小幅度等待，大幅度抛异常）
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
     * @param size 期望生成的 ID 数量
     * @return 包含 size 个 ID 的列表（实际数量可能小于 size，若 size 超出上限则截断）
     * @throws IllegalArgumentException 如果 size <= 0
     */
    public List<Long> nextIdBatch(int size) {
        // 1. 参数校验
        if (size <= 0) {
            throw new IllegalArgumentException("批量大小必须大于 0");
        }
        // 2. 截断超出的部分，避免内存溢出或长时间占用锁
        if (size > BATCH_MAX_SIZE) {
            size = BATCH_MAX_SIZE;
        }

        // 3. 批量生成（直接循环调用同步方法，保证 ID 唯一且顺序递增）
        List<Long> idList = new ArrayList<>(size);  // 预分配容量，减少扩容开销
        for (int i = 0; i < size; i++) {
            idList.add(nextId());  // nextId() 内部已同步
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
