# 📋 工单 #PROD-VOTE-010：恢复异步落库（DB 写入改 MQ 异步）

> **创建时间**：2026-09-04
> **优先级**：P1
> **设计依据**：原单体设计 `like-dislike-aggregate-count-design.md` v1.0 时序图
> **接收方**：后端工程师
> **影响服务**：`del-product`
> **依赖工单**：#PROD-VOTE-009（必须先完成）
> **预计工时**：3 小时
> **代码净变更**：约 +80 / -30

---

## 一、问题描述

### 1.1 现状

#PROD-VOTE-008 完成后，vote() 的所有操作都是**同步**：

| 操作 | 是否同步 | 耗时 |
|------|---------|------|
| 参数校验 | 同步 | ~0.5ms |
| Redis Lua | 同步 | ~1-2ms |
| `reviewMapper.insert/update/delete` | **同步** | ~5-15ms |
| `reviewMapper.selectOne`（查当前状态）| **同步** | ~5-10ms |
| invalidateLocal + publishInvalidate | 同步 | ~1ms |
| **总计** | | **~13-29ms** |

### 1.2 原文设计的预期

原文第 9 节时序图：

```
S->>CC: incrementCount(pid, +1, 0)   ← 同步 Redis（用户视角立即 +1）
S->>CC: invalidateLocal(pid)
S->>CC: cacheUserVote(...)
S->>MQ: send(VoteEvent.insert)      ← MQ 异步
S->>CC: getCount(pid)

par 异步落库
    MQ->>DB: INSERT INTO t_review
end
```

原文预期 **vote() 响应时间 3-5ms**（不等 DB），由 MQ 异步落 DB。

### 1.3 与原文的偏离

| 维度 | 原文 | 当前 | 影响 |
|------|------|------|------|
| Redis 写 | 同步 Lua | 同步 Lua | ✅ 一致 |
| DB 写 | **异步（MQ）** | **同步（insert/updateById）** | ❌ 慢了 10-25ms |
| 用户响应时间 | 3-5ms | 13-29ms | ❌ 慢了 4-10 倍 |
| MQ 削峰 | ✅ 有 | ❌ 无 | ❌ DB 故障会阻塞投票 |
| 历史日志 | 隐含保留 | **#009 待补** | 待修复 |

### 1.4 影响范围

- **高并发下 DB 成为瓶颈**：每秒投票 > 100 时，DB 连接池压力大
- **DB 故障阻塞投票**：当前 DB 不可用时 vote 接口失败
- **响应时间长**：13-29ms 比原文预期的 3-5ms 慢

---

## 二、修复方案

### 2.1 方案核心：DB 写入改 MQ 异步

```
用户点赞
  → vote() 进入
       ├─ 同步 Redis Lua（用户视角立即 +1）← 关键不变
       ├─ 同步失效 L1 + 广播
       ├─ 同步写 t_review_log（事件日志，#009 已有）← 关键不变
       ├─ 同步 MQ 发事件
       │     ↓
       │   Consumer 异步：
       │     └─ INSERT/UPDATE/DELETE t_review（当前状态）
       └─ 返回
  
响应时间回到 ~5ms（不等 DB）
```

### 2.2 三张表的写入顺序

```
vote() 内（同步）：
  ① Redis Lua（同步）        ← 用户视角立即 +1
  ② t_review_log INSERT（同步）← 永久事件记录
  
MQ Consumer（异步）：
  ③ t_review INSERT/UPDATE/DELETE（异步）← 当前状态，可从 log 重建
```

### 2.3 数据一致性保证

| 一致性要求 | 保证机制 |
|----------|---------|
| Redis 与 log 一致 | vote() 内同事务（虽然跨 Redis 和 DB，但顺序写入）|
| log 与 t_review 一致 | Consumer 内同事务 |
| Redis 与 t_review 短暂不一致 | **可接受**——Redis 是用户视角的权威 |
| Redis 丢失后可重建 | 从 log 重新聚合 |

### 2.4 Consumer 幂等性

由于 RabbitMQ at-least-once 投递，Consumer 必须保证**幂等**：

- **处理前**：用 eventId 检查 Redis SETNX（#PROD-VOTE-007 的逻辑）
- **处理中**：DB 操作根据 (user_id, order_id, product_id, is_deleted=0) 唯一键保证幂等
- **处理后**：SETNX 标记保留 24h 自动清理

---

## 三、具体改动

### 3.1 修改文件清单

| 文件 | 操作 | 改动量 |
|------|------|--------|
| `del-product/.../review/mq/VoteEvent.java` | 新增字段（action 类型细化）| +20 |
| `del-product/.../review/mq/VoteProducer.java` | 重新启用 + 新增 send 方法 | +30 |
| `del-product/.../review/mq/VoteConsumer.java` | 新增 DB 写入逻辑 + 幂等 | +60 |
| `del-product/.../review/services/impl/ReviewServiceImpl.java` | 移除同步 DB 写入，改发 MQ | -30 / +20 |

### 3.2 VoteEvent 字段扩展

**文件**：`VoteEvent.java`

```diff
  @Data
  public class VoteEvent implements Serializable {
      
+     private String eventId;        // 幂等去重（#007 已加）
      
      /** 操作类型 */
      private String action;
      
+     /** 目标状态：like / bad / null（用于 Consumer 重建）*/
+     private String targetType;
+     
+     /** 旧状态：like / bad / null（用于 Consumer 重建）*/
+     private String oldType;
      
      private Long userId;
      private Long orderId;
      private Long productId;
      private LocalDateTime eventTime;
  }
```

### 3.3 VoteProducer 重新启用

**文件**：`VoteProducer.java`

```java
/**
 * 发送投票事件（异步落 DB）
 */
public void sendVotePersistEvent(Long userId, Long orderId, Long productId,
                                  String targetType, String oldType) {
    VoteEvent event = new VoteEvent();
    event.setEventId(UUID.randomUUID().toString());
    event.setAction("PERSIST");
    event.setTargetType(targetType);
    event.setOldType(oldType);
    event.setUserId(userId);
    event.setOrderId(orderId);
    event.setProductId(productId);
    event.setEventTime(LocalDateTime.now());
    
    rabbitTemplate.convertAndSend(VOTE_QUEUE, event);
    log.info("[VoteProducer] send PERSIST event: eventId={}, target={}, old={}",
            event.getEventId(), targetType, oldType);
}
```

### 3.4 VoteConsumer 新增 PERSIST 分支

**文件**：`VoteConsumer.java`

```java
private final ReviewMapper reviewMapper;

@RabbitListener(queues = RabbitMQConfig.VOTE_QUEUE)
public void handleVoteEvent(VoteEvent event) {
    // 幂等去重（沿用 #007 逻辑）
    String key = PROCESSED_KEY_PREFIX + event.getEventId();
    Boolean firstTime = stringRedisTemplate.opsForValue()
            .setIfAbsent(key, "1", PROCESSED_TTL);
    if (Boolean.FALSE.equals(firstTime)) {
        log.info("[VoteConsumer] 重复消息，跳过: eventId={}", event.getEventId());
        return;
    }
    
    try {
        switch (event.getAction()) {
            case "PERSIST":
                handlePersist(event);  // ★ 新增分支
                break;
            default:
                log.warn("[VoteConsumer] 未知操作类型: {}", event.getAction());
        }
    } catch (Exception e) {
        stringRedisTemplate.delete(key);  // 回滚幂等标记
        throw e;
    }
}

/**
 * 异步落 DB（重建 t_review 当前状态）
 * <p>幂等保证：DB 唯一键 (userId, orderId, productId, isDeleted=0)
 */
@Transactional
private void handlePersist(VoteEvent event) {
    String targetType = event.getTargetType();
    String oldType = event.getOldType();
    
    // 1. 查当前状态
    Review existing = reviewMapper.selectOne(
            new LambdaQueryWrapper<Review>()
                    .eq(Review::getUserId, event.getUserId())
                    .eq(Review::getOrderId, event.getOrderId())
                    .eq(Review::getProductId, event.getProductId())
                    .eq(Review::getIsDeleted, 0));
    
    // 2. 按状态转移执行 DB 操作
    if (targetType == null) {
        // 取消：删除
        if (existing != null) {
            reviewMapper.physicalDeleteById(existing.getId());
        }
    } else if (existing == null && oldType == null) {
        // 新增
        Review r = new Review();
        r.setUserId(event.getUserId());
        r.setOrderId(event.getOrderId());
        r.setProductId(event.getProductId());
        r.setType("like".equals(targetType) ? 1 : 2);
        reviewMapper.insert(r);
    } else if (existing != null && oldType != null && targetType.equals(oldType)) {
        // toggle 取消（理论上不应走到这里，幂等跳过）
        // 因为 toggle 时 vote() 已经物理删除，不会有这条
    } else if (existing != null && !targetType.equals(oldType)) {
        // 切换
        Review r = new Review();
        r.setId(existing.getId());
        r.setType("like".equals(targetType) ? 1 : 2);
        reviewMapper.updateById(r);
    }
    
    log.info("[VoteConsumer] 异步落 DB 完成: eventId={}, target={}", 
            event.getEventId(), targetType);
}
```

### 3.5 ReviewServiceImpl.vote() 改造

**关键改动**：移除所有同步 DB 操作，改为 MQ 异步

```diff
  @Override
  public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
      // ... 校验代码不变 ...

-     // === 查当前状态（同步）===
-     Review existing = reviewMapper.selectOne(...);
-     String oldVote = existing == null ? null : ...;
+     // === 不再查 DB，直接基于前端状态计算 oldType ===
+     // 注意：这里简化处理，旧状态由前端传入或从 Redis userVote 缓存读
+     // 推荐方案：通过前端传入"当前已选状态"或在 vote() 内异步查 DB
+     String oldType = getOldVoteFromCacheOrDefault(userId, orderId, productId);
+     Integer oldAction = oldType == null ? null : ("like".equals(oldType) ? 1 : 2);

      // === 状态机：纯内存计算 ===
      if (Objects.equals(oldType, type)) {
          // toggle 取消
          deltaLike = "like".equals(type) ? -1 : 0;
          // ...
      } else if (oldType == null) {
          // 新增
          deltaLike = "like".equals(type) ? 1 : 0;
          // ...
      } else {
          // 切换
          deltaLike = "like".equals(type) ? 1 : -1;
          // ...
      }

+     // === 同步：Redis Lua（用户视角立即 +1）===
      if (deltaLike != 0 || deltaBad != 0) {
          reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
      }
      reviewCountCacheService.invalidateLocal(productId);
      reviewCountCacheService.publishInvalidate(productId);

+     // === 同步：写 log 表（#009 已建）===
+     // ... 同 #009

+     // === 异步：MQ 发事件让 Consumer 落 t_review ===
+     voteProducer.sendVotePersistEvent(userId, orderId, productId, type, oldType);

      return buildVoteResult(productId, type);
  }
```

### 3.6 ⚠️ 关键决策点：如何获取 oldType？

**问题**：异步落库后，vote() 内不能查 DB（那就不是异步了），需要从其他渠道获取"用户当前已选状态"。

| 方案 | 优点 | 缺点 |
|------|------|------|
| **A. 前端传入 currentVote** | 简单、前端本来就知道 | 前端需要额外维护状态 |
| **B. Redis 缓存用户投票** | 无前端依赖 | 需要保证缓存与 DB 一致 |
| **C. vote() 内异步查 DB** | 数据源可靠 | 又变同步了，违背异步初衷 |

**推荐方案 B**：复用 `reviewCountCacheService.getUserVote(userId, orderId, productId)` 方法（已存在）。

但 Redis 缓存可能丢失（被驱逐），需要 fallback：缓存 miss 时**接受降级为 sync DB 查询**（性能略降但正确）。

---

## 四、验证清单

### 4.1 编译与启动

- [ ] `del-product` 编译成功
- [ ] 重启服务，启动无异常
- [ ] RabbitMQ 队列 `vote.queue` 创建成功

### 4.2 异步落库验证

| # | 测试 | 验证步骤 |
|---|------|---------|
| 1 | 调 vote 接口后立即查 DB | **不应立即看到新记录**（异步延迟）|
| 2 | 等 100ms 后查 DB | 应该看到记录已落库 |
| 3 | 调 vote 接口后 Redis 计数 | **应立即看到 +1**（同步）|

### 4.3 幂等性验证

- [ ] 手动重投同一 eventId 3 次，DB 只有 1 条记录
- [ ] Consumer 重启后，从未消费的消息能正常处理

### 4.4 响应时间验证

- [ ] vote 接口 P99 < 10ms（对比 #008 的 13-29ms）

### 4.5 不回归验证

- [ ] 9 种状态转移场景全部正确
- [ ] t_review_log 正确写入（#009 已验证）
- [ ] Redis 计数正确（#008 已验证）
- [ ] 跨实例失效（#006 已验证）

---

## 五、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Redis 缓存丢失导致 oldType 错误 | 中 | 缓存 miss 时降级查 DB |
| MQ 消息丢失导致 t_review 不一致 | 低 | RabbitMQ 持久化 + DLQ |
| Consumer 慢导致 t_review 长期滞后 | 中 | 监控告警 + Consumer 并发 |
| 前端传入的 currentVote 不可信 | 低 | 后端必须自行校验，不依赖前端 |
| 异步与同步逻辑混用易出错 | 高 | 必须写完整状态机测试 |

---

## 六、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| #PROD-VOTE-008 | ✅ 已完成 | 状态机基础 |
| #PROD-VOTE-009 | 待执行 | **必须先完成**（log 表）|
| **#PROD-VOTE-010**（本工单）| 待执行 | **必须 #009 完成后执行** |
| #PROD-VOTE-011（可选） | 待规划 | DB 异步后，可选加 t_review 历史归档 |

---

## 七、执行顺序

```
1. #PROD-VOTE-009（先做）：加 t_review_log 表 + 同步写 log
2. #PROD-VOTE-010（本工单）：DB 写入改 MQ 异步
3. 验收 9 种状态转移 + 异步延迟 + 幂等性
4. （可选）#PROD-VOTE-011：DB 归档清理策略
```

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 联调验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会按 9 种状态转移场景 + 异步落库延迟 + 响应时间做最终验收。**