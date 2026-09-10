# 📋 工单 #PROD-VOTE-005：评价改投计数原子性修复

> **创建时间**：2026-09-04
> **优先级**：P0
> **修复方案**：方案 A（同步 Redis Lua 原子双 delta）
> **接收方**：后端工程师
> **影响服务**：`del-product`（仅此一个微服务）
> **影响范围**：C 端评价投票的"改投"场景（赞→踩 / 踩→赞）
> **预计工时**：30 分钟
> **代码净变更**：+5 行 / -8 行（净减 3 行）

---

## 一、问题描述

### 1.1 错误现象

用户对订单商品进行评价时，从"赞"切换为"踩"（或反之），**Redis 中的聚合计数偏差且无法自我修正**。

**实测表现**：
1. 用户 A 对商品 X 点赞 → `t_review` 写入 type=1，Redis `likeCount=1`
2. 用户 A 改投为踩 → `t_review` 更新 type=2，Redis `likeCount=2 badCount=1`（实际应为 `likeCount=0 badCount=1`）
3. 计数偏离真实值 +1，**永远累积**，直到下次 Caffeine 失效 + DB 回源才会修正

### 1.2 根因

`ReviewServiceImpl.vote()` 的改投分支（`else if (!oldVote.equals(type))`）只发送了"新状态 +1"的 MQ 事件，**没有发送"旧状态 -1"事件**：

```java
} else if (!oldVote.equals(type)) {
    // 改投：先回退旧计数，再增加新计数   ← 注释说要回退
    Review r = new Review();
    r.setId(existing.getId());
    r.setType("like".equals(type) ? 1 : 2);
    reviewMapper.updateById(r);

    // 改投时发送事件，MQ 消费者会处理计数更新
    // 注意：这里简化处理，实际可能需要更复杂的逻辑   ← 自承"简化"
    if ("like".equals(type)) {
        voteProducer.sendLikeEvent(userId, orderId, productId);  // 仅 +1 like
    } else {
        voteProducer.sendBadEvent(userId, orderId, productId);   // 仅 +1 bad
    }
    // ❌ 缺失：旧类型应 -1
}
```

### 1.3 影响范围

| 场景 | 后果 |
|------|------|
| 单个用户改投一次 | `likeCount` 或 `badCount` 偏 +1 |
| 多次改投 | 偏差持续累积 |
| 改投后查询 | 短窗口期显示错误计数（DB 已改但 Redis 未同步） |
| 缓存命中期间 | 用户看到的是"偏多"的计数 |

---

## 二、修复方案（方案 A：同步 Redis Lua）

### 2.1 方案核心思想

将"改投"操作从**异步 MQ 路径**改为**同步 Redis Lua 路径**：
- DB 主数据 `updateById` 一次原子写
- Redis 通过**单次 Lua 脚本调用**同时更新 `likeCount` 和 `badCount`（已有 `incrementCount(productId, deltaLike, deltaBad)` 方法支持双 delta）
- 同步失效 Caffeine L1 缓存
- **不发 MQ 事件**（避免与同步更新叠加导致双倍计数）

### 2.2 为什么选同步而非 MQ 异步

| 维度 | 同步 Lua ✅ | MQ 异步 ❌ |
|------|------------|----------|
| 原子性 | **强原子**，DB+Redis 一次请求内全部生效 | 最终一致，DB 立即生效，Redis 延迟生效 |
| MQ 故障 | 不依赖 MQ | MQ 挂掉则计数永久不更新 |
| 改投频率 | 极低频（用户极少改主意） | 不适合低频操作 |
| 代码复杂度 | +5 / -8 行 | 需新增 oldAction 字段 + 幂等去重，+30+ 行 |
| 用户体验 | 立即可见 | 几十~几百 ms 计数跳变 |

### 2.3 混合架构设计（本次仅改"改投"分支）

| 操作 | 路径 | 理由 |
|------|------|------|
| 新增赞/踩 | MQ 异步 | 高频、需要削峰、最终一致可接受 |
| 取消赞/踩 | MQ 异步 | 中频、依赖 DB 回源修正，逻辑简单 |
| **改投** | **同步 Lua** | **低频、要求原子、即改即见** |

> 改投分支独立变更，**不动新增/取消的 MQ 路径**。

---

## 三、具体改动

### 3.1 修改文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `del-product/src/main/java/com/sakana/review/services/impl/ReviewServiceImpl.java` | 修改 `else if (!oldVote.equals(type))` 分支 | +5 / -8 |

### 3.2 不修改的文件

| 文件 | 不改原因 |
|------|---------|
| `VoteEvent.java` | 改投不走 MQ，无需新增 `oldAction` 字段 |
| `VoteConsumer.java` | 改投不发 MQ，Consumer 无需新增分支 |
| `VoteController.java` | 接口签名不变 |
| `ReviewCountCacheService.java` | `incrementCount(Long, int, int)` 已支持双 delta |
| `t_review` 表结构 | 无需变更 |

### 3.3 代码 Diff

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\review\services\impl\ReviewServiceImpl.java`

**位置**：约第 92-103 行，`else if (!oldVote.equals(type))` 分支

```diff
  } else if (!oldVote.equals(type)) {
-     // 改投：先回退旧计数，再增加新计数
+     // 改投：DB 原子更新 + 同步 Redis Lua 原子双 delta
      Review r = new Review();
      r.setId(existing.getId());
      r.setType("like".equals(type) ? 1 : 2);
      reviewMapper.updateById(r);

-     // 改投时发送事件，MQ 消费者会处理计数更新
-     // 注意：这里简化处理，实际可能需要更复杂的逻辑
-     if ("like".equals(type)) {
-         voteProducer.sendLikeEvent(userId, orderId, productId);
-     } else {
-         voteProducer.sendBadEvent(userId, orderId, productId);
-     }
+     // 同步原子更新 Redis（一次 Lua 调用，同时改 likeCount 和 badCount）
+     int deltaLike = "like".equals(type) ? 1 : -1;
+     int deltaBad  = "bad".equals(type)  ? 1 : -1;
+     reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
+     // 失效 L1 本地缓存
+     reviewCountCacheService.invalidateLocal(productId);
  }
```

### 3.4 关键说明

1. **deltaLike / deltaBad 计算规则**：
   - 旧 like → 新 bad：`deltaLike=-1, deltaBad=+1`
   - 旧 bad → 新 like：`deltaLike=+1, deltaBad=-1`
   - 表达式 `"like".equals(type) ? 1 : -1` 正好覆盖以上两种情况

2. **`incrementCount` 内部使用 Lua 脚本**（已有，**无需改动**）：
   ```lua
   -- 伪代码展示
   local like_count = tonumber(redis.call('HGET', KEYS[1], 'like') or 0)
   local bad_count  = tonumber(redis.call('HGET', KEYS[1], 'bad') or 0)
   local new_like = math.max(0, like_count + tonumber(ARGV[1]))
   local new_bad  = math.max(0, bad_count  + tonumber(ARGV[2]))
   redis.call('HMSET', KEYS[1], 'like', new_like, 'bad', new_bad)
   ```
   - ✅ 单次 Redis 调用即原子
   - ✅ 守护 `likeCount` / `badCount` 不为负数

3. **`invalidateLocal` 必须调用**：让 Caffeine 失效，下次读从 Redis 拉新值，否则 Caffeine 还会返回旧数据

4. **不发 MQ 事件的原因**：避免与同步 Lua 叠加导致双倍计数

5. **Redis 异常处理**：`incrementCount` 内部已 `try/catch`，异常仅写日志不抛——DB 已写，下次读会从 DB 回源修正

---

## 四、验证清单

### 4.1 编译与启动

- [ ] `del-product` 模块编译成功：
  ```bash
  mvn clean compile -pl del-product -am -DskipTests -s settings.xml
  ```
- [ ] 重启 `del-product` 服务
- [ ] 启动日志无 `ClassNotFoundException` / `NoSuchMethodError`

### 4.2 接口验证（推荐使用 Postman / curl）

| # | 测试场景 | 操作 | 预期 likeCount | 预期 badCount |
|---|---------|------|---------------|--------------|
| 1 | 旧 like 改投为 bad | `like → bad` | 0 | 1 |
| 2 | 旧 bad 改投为 like | `bad → like` | 1 | 0 |
| 3 | 连续两次改投 | `like → bad → like` | 1 | 0 |
| 4 | 改投后再取消 | `like → bad → 取消` | 0 | 0 |

**每个场景的验证步骤**：
1. 记录改投前 Redis 状态：
   ```bash
   HGETALL review:count:{productId}
   ```
2. 通过 HTTP 调用改投接口：
   ```bash
   POST http://localhost:10010/api/v1/orders/{orderId}/items/{productId}/vote
   Body: {"type": "bad"}  # 或 "like"
   ```
3. **立即**（< 100ms）验证 Redis：
   ```bash
   HGETALL review:count:{productId}
   ```
   计数必须符合预期
4. 验证 DB 主数据：
   ```sql
   SELECT type FROM t_review WHERE user_id=? AND order_id=? AND product_id=?;
   ```
   type 字段必须为新值

### 4.3 不回归验证

- [ ] 新增赞/踩仍然走 MQ 异步路径，计数正常
- [ ] 取消赞/踩仍然走 MQ 异步路径，计数正常
- [ ] 同类型重复提交仍然幂等（计数不变）
- [ ] Redis 不可用时，DB 写入成功，接口不抛异常（仅日志告警）

---

## 五、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 同步 Redis 失败 | 低 | `incrementCount` 内部已 `try/catch`，下次读会从 DB 回源修正 |
| Lua 脚本被并发调用 | 无 | Redis 单线程执行 Lua，天然原子 |
| 与 MQ 事件重复 | 无 | 本次**不发 MQ 事件**，避免叠加 |
| 改投路径被绕过 | 无 | 改投判定逻辑未动 |
| 业务安全（订单归属） | 已有 P0 工单 #PROD-VOTE-002 | 本工单**不修复**，仅修计数逻辑 |

---

## 六、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| 本工单 #PROD-VOTE-005 | 待执行 | **独立可执行** |
| #PROD-VOTE-002（安全校验） | 待规划 | 后续工单 |
| #PROD-VOTE-004（取消事件） | 待规划 | 后续工单 |

**本工单可独立执行，不依赖其他工单。**

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 联调验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会用 4 种改投场景做最终验收。**