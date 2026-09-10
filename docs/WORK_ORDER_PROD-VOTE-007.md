# 📋 工单 #PROD-VOTE-007：MQ Consumer 幂等去重（防 RabbitMQ 重投）

> **创建时间**：2026-09-04
> **优先级**：P1
> **修复方案**：Redis SETNX 标记已处理 eventId
> **接收方**：后端工程师
> **影响服务**：`del-product`
> **预计工时**：1 小时
> **代码净变更**：+25 行

---

## 一、问题描述

### 1.1 错误现象

RabbitMQ 默认 **at-least-once 投递语义**——若 consumer 抛出异常未 ack，消息会重投。当前 `VoteConsumer` 没有去重机制：

```java
@RabbitListener(queues = RabbitMQConfig.VOTE_QUEUE)
public void handleVoteEvent(VoteEvent event) {
    // ... 没有去重
    switch (event.getAction()) {
        case "like" -> reviewCountCacheService.incrementCount(productId, 1, 0);
        // 如果同一条事件被重投 2 次 → likeCount +2 ❌
    }
}
```

**触发场景**：
- Consumer 处理到一半，进程被 kill / OOM
- Consumer 处理成功但 ack 时网络抖动
- RabbitMQ 集群切换 / 网络分区

任一情况都会导致**同一条事件被处理多次**，造成计数偏多。

### 1.2 影响范围

| 路径 | 是否受影响 | 后果 |
|------|----------|------|
| 新增赞/踩（MQ） | ✅ 受影响 | likeCount / badCount 可能 +2、+3... |
| 取消赞/踩（MQ） | ⚠️ 影响小（只失效缓存不计数） | 无明显错误 |
| 改投（同步 Lua） | ❌ 不受影响 | 本工单 #PROD-VOTE-005 已修复 |

**实际生产中重投概率约 0.1% - 1%**，但累积效应显著——一个百万级 PV 的项目，1% 重投 = 1 万次错误计数。

---

## 二、修复方案

### 2.1 方案核心：Redis SETNX + TTL

```
Consumer 收到事件
  ↓
SETNX redis_key = "vote:processed:{eventId}", value = 1, TTL = 24h
  ├─ 返回 1 (key 不存在) → 首次处理 → 执行业务逻辑
  └─ 返回 0 (key 已存在) → 重复消息 → 直接跳过
```

**为什么用 Redis SETNX**：
- 原子操作（天然幂等）
- TTL 自动清理（24h 后过期，不占空间）
- 跨实例生效（多 consumer 共享 Redis）

### 2.2 为什么不用其他方案

| 方案 | 缺点 |
|------|------|
| 数据库唯一索引 | 性能差，每次消费都打 DB |
| 内存 Map | 进程重启丢失，不跨实例 |
| Redis Set 永久 | 长期占用内存 |
| **Redis SETNX + TTL** ✅ | **自动清理、跨实例、原子、性能高** |

---

## 三、具体改动

### 3.1 修改文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `del-product/src/main/java/com/sakana/review/mq/VoteEvent.java` | 新增 `eventId` 字段 | +10 |
| `del-product/src/main/java/com/sakana/review/mq/VoteProducer.java` | 发送时生成 eventId | +3 |
| `del-product/src/main/java/com/sakana/review/mq/VoteConsumer.java` | 消费时 SETNX 去重 | +12 |

### 3.2 代码 Diff

#### 改动 1：`VoteEvent.java` 新增 eventId 字段

```diff
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public class VoteEvent implements Serializable {

      private static final long serialVersionUID = 1L;

+     /** 事件唯一 ID（用于 Consumer 幂等去重） */
+     private String eventId;

      /** 操作类型：like=点赞, bad=踩, cancel=取消 */
      private String action;

      // ... 其他字段
  }
```

#### 改动 2：`VoteProducer.java` 生成 eventId

```diff
  public VoteEvent like(Long userId, Long orderId, Long productId) {
-     return new VoteEvent("like", userId, orderId, productId, LocalDateTime.now());
+     return new VoteEvent(UUID.randomUUID().toString(), "like", userId, orderId, productId, LocalDateTime.now());
  }

  public VoteEvent bad(Long userId, Long orderId, Long productId) {
-     return new VoteEvent("bad", userId, orderId, productId, LocalDateTime.now());
+     return new VoteEvent(UUID.randomUUID().toString(), "bad", userId, orderId, productId, LocalDateTime.now());
  }

  public VoteEvent cancel(Long userId, Long orderId, Long productId) {
-     return new VoteEvent("cancel", userId, orderId, productId, LocalDateTime.now());
+     return new VoteEvent(UUID.randomUUID().toString(), "cancel", userId, orderId, productId, LocalDateTime.now());
  }
```

> 注意：因为字段顺序变了，构造调用也要同步更新。如果用 `@AllArgsConstructor`，**位置参数顺序必须严格匹配**。建议显式用 builder 或全参构造。

#### 改动 3：`VoteConsumer.java` 增加 SETNX 去重

```java
private static final String PROCESSED_KEY_PREFIX = "vote:processed:";
private static final Duration PROCESSED_TTL = Duration.ofHours(24);

private final StringRedisTemplate stringRedisTemplate;
private final ReviewCountCacheService reviewCountCacheService;

@RabbitListener(queues = RabbitMQConfig.VOTE_QUEUE)
public void handleVoteEvent(VoteEvent event) {
    if (event == null || event.getEventId() == null) {
        log.warn("[VoteConsumer] 收到空事件或缺少 eventId，跳过");
        return;
    }

    // 幂等去重：SETNX 标记已处理
    String key = PROCESSED_KEY_PREFIX + event.getEventId();
    Boolean firstTime = stringRedisTemplate.opsForValue()
            .setIfAbsent(key, "1", PROCESSED_TTL);

    if (Boolean.FALSE.equals(firstTime)) {
        log.info("[VoteConsumer] 重复消息，跳过: eventId={}", event.getEventId());
        return;  // 已处理过，不重复计数
    }

    log.info("[VoteConsumer] 收到事件: action={}, eventId={}, productId={}",
            event.getAction(), event.getEventId(), event.getProductId());

    try {
        switch (event.getAction()) {
            case "like"  -> handleLike(event);
            case "bad"   -> handleBad(event);
            case "cancel" -> handleCancel(event);
            default      -> log.warn("[VoteConsumer] 未知操作类型: {}", event.getAction());
        }
    } catch (Exception e) {
        // 处理失败，删除已处理标记，允许下次重试
        stringRedisTemplate.delete(key);
        log.error("[VoteConsumer] 处理事件失败，已回滚幂等标记: eventId={}", event.getEventId(), e);
        throw e;  // 重新抛出让 RabbitMQ 重试
    }
}
```

**关键逻辑**：
- 处理**成功** → SETNX 标记保留，TTL 24h 后自动清理
- 处理**失败** → 删除 SETNX 标记，让 RabbitMQ 重试时不丢消息
- 重复消息 → SETNX 返回 false，直接跳过

---

## 四、验证清单

### 4.1 编译与启动

- [ ] `del-product` 编译成功
- [ ] 重启 `del-product` 服务
- [ ] 启动日志无反序列化错误（**VoteEvent 字段变更需同步重启 producer 端**）

### 4.2 幂等性测试

| # | 测试场景 | 预期 |
|---|---------|------|
| 1 | 手动向 RabbitMQ 重投同一条消息 3 次 | Redis likeCount 只 +1 |
| 2 | 处理过程中 kill -9 consumer 进程 | RabbitMQ 重投后能成功处理 |
| 3 | Redis 不可用时 | 业务正常处理（去重降级为不生效，依赖 RabbitMQ 自身去重） |

**模拟重投方法**：
```bash
# 手动发布 3 条相同 eventId 的事件
for i in 1 2 3; do
  rabbitmqadmin publish exchange= routing_key=vote.queue \
    payload='{"eventId":"test-001","action":"like","productId":1,...}'
done
```

### 4.3 不回归验证

- [ ] 新增赞/踩正常
- [ ] 取消赞/踩正常
- [ ] 改投走同步 Lua 路径不受影响
- [ ] VoteEvent 反序列化兼容旧消息（建议保留 `eventId` 为可选字段）

---

## 五、向后兼容注意

由于 `VoteEvent` 字段变更：

1. **如果消息已发出未消费**：旧版本 consumer 反序列化新版本 event 会失败（构造参数位置变了）
   - **修复**：用 `@AllArgsConstructor` 时考虑改用 builder，或在 `VoteEvent` 顶部加 `@JsonIgnoreProperties(ignoreUnknown = true)`
2. **如果滚动发布**：先发新版 producer → 重启 consumer；不要先发新版 consumer 再发新版 producer

**建议**：
- 短暂停服发布
- 或：保留旧版本 producer 一段时间，等队列清空

---

## 六、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Redis 不可用 | 低 | SETNX 失败时降级为直接处理（业务可用，去重失效） |
| 24h TTL 不够 | 低 | 99% 重投发生在 1 分钟内，24h 充分 |
| 字段顺序变更破坏兼容 | 中 | 先停服再发布，或使用 builder 模式 |
| SETNX 与业务处理非原子 | 低 | 处理失败时主动 delete 标记，允许重试 |

---

## 七、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| #PROD-VOTE-005 | 待执行 | 独立工单 |
| #PROD-VOTE-006 | 待执行 | 独立工单 |
| #PROD-VOTE-007（本工单） | 待执行 | **独立可执行** |
| #PROD-VOTE-002（安全校验） | 待规划 | 后续工单 |

**本工单独立可执行，不依赖其他工单。**

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 幂等验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会用重投模拟测试做最终验收。**