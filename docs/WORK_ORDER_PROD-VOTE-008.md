# 📋 工单 #PROD-VOTE-008：评价投票 toggle 语义 + 全部走同步 Redis Lua

> **创建时间**：2026-09-04
> **优先级**：P0
> **修复方案**：完整状态机改造 + 全部走同步 Redis Lua
> **设计依据**：`E:\Idea_project\SpringBoot_Delivery\docs\like-dislike-aggregate-count-design.md` v1.0
> **接收方**：后端工程师
> **影响服务**：`del-product`（仅此一个微服务）
> **预计工时**：2 小时
> **代码净变更**：`ReviewServiceImpl.vote()` 整体重写（约 +60 / -40）

---

## 一、问题描述

### 1.1 错误现象

聚合计数存在两处明确的偏差：

| 测试场景 | 当前实际 | 期望 |
|---------|---------|------|
| 已赞 → 再点 👍（双击） | likeCount 不变 ❌ | likeCount -1（取消）|
| 已赞 → 显式取消 | likeCount 不变 ❌ | likeCount -1 |

且**新增路径**走 MQ 异步，与**改投路径**走同步 Lua 不一致，存在极短的不一致窗口。

### 1.2 根因

`ReviewServiceImpl.vote()` 当前实现把"同步改 Redis"和"异步落 DB"的职责**完全搞反**：

| 路径 | 写 Redis | 写 DB | 问题 |
|------|---------|-------|------|
| 新增（oldVote=null） | MQ 异步 ❌ | 同步 ✓ | 异步窗口期 stale |
| 改投 | 同步 Lua（#005）| 同步 | OK |
| 取消（type=null） | MQ 异步 + **不更新 count** ❌❌ | 同步 | 永远偏多 |
| 同类型再提交 | 幂等跳过 ❌ | 跳过 | 应该是取消 |

### 1.3 与原单体设计的偏离

原文设计（`E:\Idea_project\SpringBoot_Delivery\docs\like-dislike-aggregate-count-design.md` v1.0）明确规定：

> "投票接口直接读 Redis 强一致"——同步 Redis Lua 更新计数
> "MQ 异步落 DB"——MQ 只用于 DB 落盘，不参与计数

当前微服务实现把职责搞反了。本次工单将把微服务**移植对齐到原设计**。

---

## 二、修复方案

### 2.1 方案核心：完整状态机 + 全部同步 Lua

```
┌─────────┐  like   ┌─────────┐
│  (无)   │ ──────▶ │  已赞   │
│ like:0  │  ◀────  │ like:+1 │
│ bad:0   │  bad    └─────────┘
└─────────┘           │ like │ bad
     │ bad            │      │
     ▼ like           │      ▼
┌─────────┐   bad  ┌─────────┐
│  已踩   │ ◀──── │  已赞   │
│ bad:+1  │       │         │
└─────────┘       └─────────┘
```

**所有 6 种状态转移** 全部通过同步 Redis Lua 原子更新计数：

| 转移 | 触发条件 | DB 操作 | Lua delta |
|------|---------|---------|-----------|
| 1 | (无) + like | INSERT type=1 | (+1, 0) |
| 2 | (无) + bad | INSERT type=2 | (0, +1) |
| 3 | 已赞 + like（再点） | DELETE | (-1, 0) |
| 4 | 已踩 + bad（再点） | DELETE | (0, -1) |
| 5 | 已赞 + bad（切换）| UPDATE type=2 | (-1, +1) |
| 6 | 已踩 + like（切换）| UPDATE type=1 | (+1, -1) |

### 2.2 Toggle 语义

- **前端不维护状态**，仅发送"我点了哪个按钮"（type=like/bad）
- **后端判定状态转移**：
  - 当前无记录 → 新增
  - 当前已 like + 传 type=like → 取消 like（toggle 关闭）
  - 当前已 like + 传 type=bad → 切换到 bad
  - 当前已 bad + 传 type=bad → 取消 bad
  - 当前已 bad + 传 type=like → 切换到 like
- **显式取消**：传 type=null（与原行为兼容）

### 2.3 与原单体设计的对齐

| 原设计原则 | 本次工单实现 |
|-----------|------------|
| Redis 是用户视角的权威 | ✅ 同步 Lua 改 Redis（先于返回）|
| MQ 仅用于异步落 DB | ✅ vote() 不再发 MQ 计数事件 |
| L1 写后立即失效 | ✅ 维持原 #PROD-VOTE-006 |
| 物理删除（非软删除）| ✅ 维持原实现 |
| 30s TTL 兜底 | ✅ 维持原 CaffeineConfig |

---

## 三、具体改动

### 3.1 修改文件清单

| 文件 | 操作 | 改动量 |
|------|------|--------|
| `del-product/src/main/java/com/sakana/review/services/impl/ReviewServiceImpl.java` | **重写** `vote()` 方法 | +60 / -40 |

### 3.2 不修改的文件

| 文件 | 不改原因 |
|------|---------|
| `ReviewCountCacheServiceImpl.java` | `incrementCount(pid, deltaLike, deltaBad)` 已支持任意 delta，无需改 |
| `CaffeineConfig.java` | TTL 30s 维持不变 |
| `RedisConfig.java` | Pub/Sub 容器维持不变 |
| `CacheInvalidateListener.java` | 跨实例失效维持不变 |
| `VoteEvent.java` / `VoteProducer.java` / `VoteConsumer.java` | **本次不删**，但 `vote()` 不再调用它们处理 like/bad/cancel 计数。可作为后续审计/通知用途保留 |

### 3.3 代码 Diff

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\review\services\impl\ReviewServiceImpl.java`

**位置**：`vote(Long userId, Long orderId, Long productId, String type)` 方法整体重写

```diff
  @Override
  public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
+     // ========== 1. 参数校验 ==========
      if (type != null && !"like".equals(type) && !"bad".equals(type)) {
          throw new BizException(ReviewErrorCode.REVIEW_VOTE_PARAM_INVALID);
      }

+     // ========== 2. 开关校验（仅 like/bad 需检查）==========
      ReviewSwitchVO sw = getSwitch();
      if ("like".equals(type) && !sw.isPraiseOpen()) {
          throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
      }
      if ("bad".equals(type) && !sw.isBadOpen()) {
          throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
      }

+     // ========== 3. 查询当前状态 ==========
      Review existing = reviewMapper.selectOne(
              new LambdaQueryWrapper<Review>()
                      .eq(Review::getUserId, userId)
                      .eq(Review::getOrderId, orderId)
                      .eq(Review::getProductId, productId)
                      .eq(Review::getIsDeleted, 0));
      String oldVote = existing == null ? null 
                      : (existing.getType() == 1 ? "like" : "bad");

+     // ========== 4. 状态机：计算 delta + 执行 DB 操作 ==========
+     int deltaLike = 0;
+     int deltaBad  = 0;
+
+     if (Objects.equals(oldVote, type)) {
+         // === 转移 3 / 4：同类型再提交 = 取消（toggle 关闭）===
+         if (oldVote != null) {
+             reviewMapper.physicalDeleteById(existing.getId());
+             if ("like".equals(oldVote)) deltaLike = -1;
+             else                        deltaBad  = -1;
+             log.info("[评价] toggle 取消: userId={}, productId={}, old={}", userId, productId, oldVote);
+         } else {
+             // 旧无 + 新无（type=null 且无记录）= 幂等 no-op
+             log.debug("[评价] 无记录显式取消，幂等跳过: userId={}", userId);
+         }
+     } else if (oldVote == null) {
+         // === 转移 1 / 2：新增 ===
+         if (type == null) {
+             // 无记录 + 显式取消 = no-op（上面已处理）
+             // 此分支实际不会进入（因为 oldVote=null 且 type=null 在第一个 if 中走幂等）
+         } else {
+             Review r = new Review();
+             r.setUserId(userId);
+             r.setOrderId(orderId);
+             r.setProductId(productId);
+             r.setType("like".equals(type) ? 1 : 2);
+             reviewMapper.insert(r);
+             if ("like".equals(type)) deltaLike = +1;
+             else                     deltaBad  = +1;
+             log.info("[评价] 新增: userId={}, productId={}, type={}", userId, productId, type);
+         }
+     } else {
+         // === 转移 5 / 6：切换 ===
+         Review r = new Review();
+         r.setId(existing.getId());
+         r.setType("like".equals(type) ? 1 : 2);
+         reviewMapper.updateById(r);
+
+         // 旧类型 -1，新类型 +1
+         if ("like".equals(oldVote)) deltaLike = -1;
+         else                        deltaBad  = -1;
+         if ("like".equals(type))    deltaLike += 1;
+         else                        deltaBad  += 1;
+         log.info("[评价] 切换: userId={}, productId={}, {}->{}", userId, productId, oldVote, type);
+     }

+     // ========== 5. 同步原子更新 Redis（一次 Lua 调用）==========
+     if (deltaLike != 0 || deltaBad != 0) {
+         reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
+     }

+     // ========== 6. 失效 L1 + 跨实例广播 ==========
+     reviewCountCacheService.invalidateLocal(productId);
+     reviewCountCacheService.publishInvalidate(productId);
+     reviewCountCacheService.invalidateUserVote(userId, orderId, productId);

+     return buildVoteResult(productId, type);
  }
```

### 3.4 关键变更点

| 维度 | 旧实现 | 新实现 |
|------|--------|--------|
| 同类型再提交 | 幂等跳过 ❌ | toggle 取消 ✅ |
| 显式取消 (type=null) | MQ 异步 + 不动 count ❌ | 同步 Lua -1 ✅ |
| 新增 (oldVote=null) | MQ 异步 +1 ❌ | 同步 Lua +1 ✅ |
| 改投 (oldVote != type) | 同步 Lua（#005）| 同步 Lua（保持）|
| MQ 调用 | 3 处（sendLike/Bad/Cancel）| **0 处** |
| DB 写入 | 同步 | 同步（保持） |

### 3.5 需新增 import

```java
import java.util.Objects;  // Objects.equals 用于同类型判定
```

---

## 四、6 种状态转移验证矩阵

每种状态转移都需执行单元测试或接口联调验证：

| # | 旧状态 | 操作 | 预期 DB | 预期 Redis | 测试方法 |
|---|--------|------|---------|-----------|---------|
| 1 | (无) | like | INSERT type=1 | (+1, 0) | HGETALL + SELECT |
| 2 | (无) | bad | INSERT type=2 | (0, +1) | 同上 |
| 3 | 已赞 | like（再点）| DELETE | (-1, 0) | **本工单新增验证** |
| 4 | 已踩 | bad（再点）| DELETE | (0, -1) | **本工单新增验证** |
| 5 | 已赞 | bad（切换）| UPDATE type=2 | (-1, +1) | #005 已验证 |
| 6 | 已踩 | like（切换）| UPDATE type=1 | (+1, -1) | #005 已验证 |
| 7 | 已赞 | null（显式取消）| DELETE | (-1, 0) | **本工单新增验证** |
| 8 | 已踩 | null（显式取消）| DELETE | (0, -1) | **本工单新增验证** |
| 9 | (无) | null | no-op | (0, 0) | 边界 |

---

## 五、验证清单

### 5.1 编译与启动

- [ ] `del-product` 模块编译成功
- [ ] 重启 `del-product` 服务
- [ ] 启动日志无 `ClassNotFoundException`

### 5.2 接口验证（必做）

| # | 测试场景 | 操作 | 预期 likeCount | 预期 badCount |
|---|---------|------|---------------|--------------|
| 1 | 新增 like | POST type=like（首次）| 1 | 0 |
| 2 | 切换 like→bad | POST type=bad | 0 | 1 |
| 3 | 切换 bad→like | POST type=like | 1 | 0 |
| 4 | 双击取消 like | 连续两次 POST type=like | 0 | 0 |
| 5 | 显式取消 | POST type=null | 0 | 0 |
| 6 | 边界：取消不存在的记录 | POST type=null（无记录）| 不变 | 不变 |

每个场景验证步骤：
1. 记录操作前 `HGETALL review:count:{productId}`
2. HTTP 调用接口
3. **立即**（< 100ms）验证 Redis 计数
4. 验证 DB `t_review` 表内容（INSERT/UPDATE/DELETE 是否正确）

### 5.3 跨实例一致性（#006 仍需验证）

- [ ] 启动 2 个 `del-product` 实例
- [ ] 实例 A 改投 / 取消
- [ ] 实例 B 立即 `getCount` 验证返回新值

### 5.4 不回归验证

- [ ] MQ Consumer 不再被 like/bad/cancel 事件触发（应无相关日志）
- [ ] 开关功能（AdminReviewView）正常
- [ ] 商品列表 / 详情页的聚合计数显示正常

---

## 六、MQ 路径的处置

### 6.1 当前 MQ 角色变化

`vote()` 不再调用 `voteProducer.sendLikeEvent / sendBadEvent / sendCancelEvent`：
- `VoteProducer` 仍然存在（代码保留）
- `VoteConsumer.handleLike / handleBad / handleCancel` 的 `incrementCount` 调用变成**死代码**（永远不会触发）

### 6.2 处置建议（**不在本工单范围**）

- **方案 A**（推荐）：彻底删除 `VoteProducer` 三个 `sendXxxEvent` 方法 + `VoteConsumer` 三个 `handleXxx` 分支 + 对应 switch case
- **方案 B**：保留 MQ 框架，未来用于"异步通知 / 审计 / 数据分析"等扩展用途

> 后端工程师可自行决定，但**必须明确告知运维**：vote 计数不再走 MQ。

### 6.3 #PROD-VOTE-007 的影响

之前为 MQ 幂等去重做的 #PROD-VOTE-007（`eventId` + SETNX）**在计数路径上变得不那么关键**，因为 vote() 已经不发 MQ 了。但 SETNX 代码保留无副作用，不需回退。

---

## 七、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 同步 Lua 失败导致 DB 已改但 Redis 未更新 | 低 | `incrementCount` 内部 try/catch，下次读 DB 回源 |
| Toggle 语义对前端不友好 | 低 | 前端只需传"我点了哪个"，不需维护状态 |
| MQ 路径不再使用产生代码冗余 | 低 | 后续可清理（不在本工单）|
| 取消后 30s 内其他实例读到旧值 | 中 | #006 Pub/Sub 已解决 |
| 极端 race condition（同一用户并发点击）| 低 | 唯一键 + DB 行锁保证最终一致 |

---

## 八、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| #PROD-VOTE-005 | ✅ 已完成 | 本工单**包含并扩展** #005 |
| #PROD-VOTE-006 | ✅ 已完成 | 本工单**保留使用**（跨实例失效）|
| #PROD-VOTE-007 | ✅ 已完成 | **降级为可选**（MQ 不再用于计数）|
| #PROD-VOTE-008（本工单）| 待执行 | **独立可执行**，但建议在 #005 基础上做 |
| #PROD-VOTE-002（安全校验）| 待规划 | 后续工单 |

**本工单可独立执行，不依赖其他工单。**

---

## 九、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 联调验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会用 9 种状态转移场景做最终验收。**

---

## 附录 A：设计依据引用

本文档核心思想来自单体应用设计：

> `E:\Idea_project\SpringBoot_Delivery\docs\like-dislike-aggregate-count-design.md` v1.0

原文核心原则：
- 第 5 节："投票接口直接读 Redis 强一致"
- 第 9 节时序图：`incrementCount` 同步在请求线程调用，MQ 仅用于异步落 DB
- 第 11 节："事实存表，计数聚合（实时 + 三级缓存），写走 Redis Lua 原子递增 + MQ 异步落 DB"

**本工单 = 原文设计在微服务架构下的移植实现**。