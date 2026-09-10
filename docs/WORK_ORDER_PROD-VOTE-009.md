# 📋 工单 #PROD-VOTE-009：新增 t_review_log 事件日志表

> **创建时间**：2026-09-04
> **优先级**：P0
> **设计依据**：原单体设计 `like-dislike-aggregate-count-design.md` v1.0 + 架构改进
> **接收方**：后端工程师
> **影响服务**：`del-product`
> **预计工时**：2 小时
> **代码净变更**：+50 行（含新实体、Mapper、vote() 加 log 写入）

---

## 一、问题描述

### 1.1 现状分析

当前评价系统的数据存储：

| 表 | 存储内容 | 用途 | 局限性 |
|----|---------|------|--------|
| `t_review` | 当前状态（每用户每订单每商品一条）| toggle 语义所需 | 物理删除会丢历史 |
| Redis Hash | 全局聚合计数 | 热数据缓存 | 丢失后不可恢复 |

**核心问题**：

1. **历史事件无法追溯**：用户点过赞→取消→再赞→改踩，最终 `t_review` 只有一条记录（type=2），中间过程丢失
2. **管理员面板数据无来源**：需要"上周点赞 TOP10"、"用户活跃度趋势"、"取消率"等聚合分析，但 `t_review` 无法提供
3. **Redis 丢失后无法重建**：原文设计只存"事实"在 `t_review`，但物理删除后历史事实也没了

### 1.2 与原单体设计的偏离

原单体设计第 1.2 节：

> "事实存表，计数聚合（实时 + 三级缓存），写走 Redis Lua 原子递增 + MQ 异步落 DB"

原文虽然主张"实时聚合 + 三级缓存"，但隐含假设是 **`t_review` 记录的事实是永久保留的**。当前微服务实现的"物理删除 + 不记录历史事件"已经偏离了这个核心假设。

### 1.3 影响范围

| 业务场景 | 当前能否支持 |
|---------|------------|
| 用户当前投票状态查询 | ✅ 支持（t_review）|
| 商品全局聚合计数查询 | ✅ 支持（Redis + DB 回源）|
| 管理员"昨日点赞 TOP10"| ❌ 无法支持 |
| 管理员"用户活跃度趋势"| ❌ 无法支持 |
| 管理员"反复横跳用户分析"| ❌ 无法支持 |
| Redis 数据丢失后重建 | ❌ 无法支持 |

---

## 二、修复方案

### 2.1 方案核心：新增 `t_review_log` 事件日志表

在 `t_review` 主表之外新增 `t_review_log`，**记录每一次评价事件的不可变日志**：

```
t_review_log（事件流，append-only）
├─ event 1: user=A, product=X, action=LIKE,         time=T1
├─ event 2: user=A, product=X, action=CANCEL_LIKE,  time=T2
├─ event 3: user=A, product=X, action=LIKE,         time=T3
├─ event 4: user=A, product=X, action=CHANGE_TO_BAD, time=T4
└─ ...
```

**关键设计**：

- **不可变**（append-only）：不 UPDATE、不 DELETE
- **同步写入**：在 `vote()` 内同步插入，保证事件不丢失
- **唯一永久保留**：即使 `t_review` 主表被物理删除，log 永远在

### 2.2 action 枚举

| action 值 | 含义 | 触发场景 |
|----------|------|---------|
| 1 | LIKE | 新增赞（从无到赞）|
| 2 | BAD | 新增踩（从无到踩）|
| 3 | CANCEL_LIKE | 取消赞（toggle 或显式取消赞）|
| 4 | CANCEL_BAD | 取消踩（toggle 或显式取消踩）|
| 5 | CHANGE_LIKE_TO_BAD | 改投：赞→踩 |
| 6 | CHANGE_BAD_TO_LIKE | 改投：踩→赞 |

### 2.3 三张表最终职责

| 表 | 用途 | 是否可重建 | 写入方式（当前）| 写入方式（最终）|
|----|------|----------|--------------|--------------|
| `t_review_log` | 事件日志，永久保存 | ❌ 不可重建（事实源）| **本次新增** | 同步（vote() 内） |
| `t_review` | 当前状态快照 | ✅ 可从 log 重建 | 同步 | 异步（#010 改造）|
| Redis Hash | 全局聚合计数缓存 | ✅ 可从 log 重建 | 同步 Lua | 同步 Lua（保持）|

---

## 三、具体改动

### 3.1 修改文件清单

| 文件 | 操作 | 改动量 |
|------|------|--------|
| `del-product/.../review/dao/entity/ReviewLog.java` | **新建** | +50 |
| `del-product/.../review/dao/mapper/ReviewLogMapper.java` | **新建** | +15 |
| `del-product/.../review/enums/ReviewAction.java` | **新建** | +30 |
| `del-product/src/main/resources/db/migration/V{timestamp}__create_t_review_log.sql` | **新建** SQL 脚本 | +25 |
| `del-product/.../review/services/impl/ReviewServiceImpl.java` | 修改 `vote()` 6 个分支各加 log | +30 |

### 3.2 SQL 脚本

**文件**：`del-product/src/main/resources/db/migration/V20260904__create_t_review_log.sql`

```sql
-- ============================================================
-- 评价事件日志表：记录每次评价动作的不可变日志
-- 设计目的：管理员面板数据来源、Redis 重建源、审计追溯
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_review_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                COMMENT '评价用户ID',
    `order_id`    BIGINT       NOT NULL                COMMENT '所属订单ID',
    `product_id`  BIGINT       NOT NULL                COMMENT '被评商品ID',
    `action`      TINYINT      NOT NULL                COMMENT '1=赞 2=踩 3=取消赞 4=取消踩 5=赞改踩 6=踩改赞',
    `old_action`  TINYINT           DEFAULT NULL       COMMENT '改投前的 action（NULL 表示新增）',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间（毫秒精度）',
    PRIMARY KEY (`id`),
    
    -- ★ 聚合查询走它：商品维度时间序列
    INDEX `idx_log_product_time` (`product_id`, `created_at`),
    
    -- ★ 用户行为分析
    INDEX `idx_log_user_time` (`user_id`, `created_at`),
    
    -- ★ 全局趋势
    INDEX `idx_log_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价事件日志（append-only）';
```

### 3.3 实体类

**文件**：`ReviewLog.java`

```java
package com.sakana.review.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价事件日志实体
 * <p>
 * 不可变日志，记录每次评价动作的完整历史。
 * 用于：管理员面板聚合分析、Redis 重建、审计追溯。
 */
@Data
@TableName("t_review_log")
public class ReviewLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long orderId;
    private Long productId;
    /** 1=赞 2=踩 3=取消赞 4=取消踩 5=赞改踩 6=踩改赞 */
    private Integer action;
    /** 改投前的 action（NULL 表示新增）*/
    private Integer oldAction;
    private LocalDateTime createdAt;
}
```

### 3.4 枚举类

**文件**：`ReviewAction.java`

```java
package com.sakana.review.enums;

import lombok.Getter;

/**
 * 评价动作枚举
 */
@Getter
public enum ReviewAction {
    LIKE(1, "新增赞"),
    BAD(2, "新增踩"),
    CANCEL_LIKE(3, "取消赞"),
    CANCEL_BAD(4, "取消踩"),
    CHANGE_TO_BAD(5, "赞改踩"),
    CHANGE_TO_LIKE(6, "踩改赞");

    private final int code;
    private final String desc;

    ReviewAction(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

### 3.5 Mapper

**文件**：`ReviewLogMapper.java`

```java
package com.sakana.review.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.review.dao.entity.ReviewLog;

public interface ReviewLogMapper extends BaseMapper<ReviewLog> {
}
```

### 3.6 vote() 方法改动

**文件**：`ReviewServiceImpl.java`

在每个状态分支里同步写 log（**关键改动**）：

```diff
  @Override
  public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
      // ... 校验代码不变 ...

+     // 用于记录 log
+     Integer oldAction = oldVote == null ? null 
+             : ("like".equals(oldVote) ? ReviewAction.LIKE.getCode() : ReviewAction.BAD.getCode());

      if (type == null) {
          // ── 取消操作 ──
          if (existing != null) {
              int deltaLike = existing.getType() == 1 ? -1 : 0;
              int deltaBad  = existing.getType() == 2 ? -1 : 0;
              reviewMapper.physicalDeleteById(existing.getId());
              syncRedisCount(productId, deltaLike, deltaBad);
+             // ★ 同步写 log
+             int cancelAction = existing.getType() == 1 
+                     ? ReviewAction.CANCEL_LIKE.getCode() 
+                     : ReviewAction.CANCEL_BAD.getCode();
+             reviewLogMapper.insert(buildLog(userId, orderId, productId, cancelAction, oldAction));
              log.info("[投票] 取消 vote: ...");
          }
      } else if (oldVote == null) {
          // ── 新增投票 ──
          Review r = new Review();
          r.setUserId(userId);
          r.setOrderId(orderId);
          r.setProductId(productId);
          r.setType("like".equals(type) ? 1 : 2);
          reviewMapper.insert(r);

          int deltaLike = "like".equals(type) ? 1 : 0;
          int deltaBad  = "bad".equals(type)  ? 1 : 0;
          syncRedisCount(productId, deltaLike, deltaBad);
+         // ★ 同步写 log
+         int newAction = "like".equals(type) 
+                 ? ReviewAction.LIKE.getCode() 
+                 : ReviewAction.BAD.getCode();
+         reviewLogMapper.insert(buildLog(userId, orderId, productId, newAction, null));
          log.info("[投票] 新增 vote: ...");
      } else if (oldVote.equals(type)) {
          // ── Toggle 取消 ──
          int deltaLike = "like".equals(type) ? -1 : 0;
          int deltaBad  = "bad".equals(type)  ? -1 : 0;
          reviewMapper.physicalDeleteById(existing.getId());
          syncRedisCount(productId, deltaLike, deltaBad);
+         // ★ 同步写 log
+         int toggleAction = "like".equals(type) 
+                 ? ReviewAction.CANCEL_LIKE.getCode() 
+                 : ReviewAction.CANCEL_BAD.getCode();
+         reviewLogMapper.insert(buildLog(userId, orderId, productId, toggleAction, oldAction));
          log.info("[投票] Toggle取消 vote: ...");
      } else {
          // ── 切换投票 ──
          Review r = new Review();
          r.setId(existing.getId());
          r.setType("like".equals(type) ? 1 : 2);
          reviewMapper.updateById(r);

          int deltaLike = "like".equals(type) ? 1 : -1;
          int deltaBad  = "bad".equals(type)  ? 1 : -1;
          syncRedisCount(productId, deltaLike, deltaBad);
+         // ★ 同步写 log
+         int changeAction = "like".equals(type) 
+                 ? ReviewAction.CHANGE_TO_LIKE.getCode() 
+                 : ReviewAction.CHANGE_TO_BAD.getCode();
+         reviewLogMapper.insert(buildLog(userId, orderId, productId, changeAction, oldAction));
          log.info("[投票] 切换 vote: ...");
      }
      
      // ... 后续代码不变 ...
  }

+ /**
+  * 构造事件日志对象
+  */
+ private ReviewLog buildLog(Long userId, Long orderId, Long productId, int action, Integer oldAction) {
+     ReviewLog log = new ReviewLog();
+     log.setUserId(userId);
+     log.setOrderId(orderId);
+     log.setProductId(productId);
+     log.setAction(action);
+     log.setOldAction(oldAction);
+     return log;
+ }
```

### 3.7 vote() 注入新增依赖

```diff
  private final ReviewMapper reviewMapper;
  private final ReviewLogMapper reviewLogMapper;  // ★ 新增
  private final ReviewCountCacheService reviewCountCacheService;
```

---

## 四、验证清单

### 4.1 数据库

- [ ] Flyway/Liquibase 脚本执行成功
- [ ] 表结构正确，3 个索引都建好
- [ ] 字符集为 utf8mb4

### 4.2 编译与启动

- [ ] `del-product` 编译成功
- [ ] 重启服务，启动无异常

### 4.3 接口验证（必做）

每个状态转移场景都验证 `t_review_log` 是否记录：

| # | 测试 | 预期 log 记录 |
|---|------|------------|
| 1 | (无) → like | 1 条 action=1, old_action=NULL |
| 2 | (无) → bad | 1 条 action=2, old_action=NULL |
| 3 | 已赞 → like（toggle）| 1 条 action=3, old_action=1 |
| 4 | 已踩 → bad（toggle）| 1 条 action=4, old_action=2 |
| 5 | 已赞 → bad（切换）| 1 条 action=5, old_action=1 |
| 6 | 已踩 → like（切换）| 1 条 action=6, old_action=2 |
| 7 | 已赞 → null（取消）| 1 条 action=3, old_action=1 |
| 8 | 已踩 → null（取消）| 1 条 action=4, old_action=2 |
| 9 | (无) → null（no-op）| **0 条**（不写 log）|

### 4.4 管理员面板查询示例验证

```sql
-- 昨日新增点赞 TOP 10
SELECT product_id, COUNT(*) AS cnt
FROM t_review_log
WHERE action IN (1, 6) AND created_at > '2026-09-03'
GROUP BY product_id
ORDER BY cnt DESC
LIMIT 10;

-- 取消率
SELECT 
    SUM(CASE WHEN action IN (3,4) THEN 1 ELSE 0 END) * 1.0 / COUNT(*) AS cancel_rate
FROM t_review_log;

-- 反复横跳用户
SELECT user_id, COUNT(*) AS change_cnt
FROM t_review_log
WHERE action IN (5, 6)
GROUP BY user_id
HAVING change_cnt > 5
ORDER BY change_cnt DESC;
```

---

## 五、Redis 重建 SQL（运维工具，附在工单里）

如果未来 Redis 数据丢失，可用以下 SQL 重新计算聚合计数：

```sql
-- 从 log 表重新计算每个商品的最终聚合
SELECT 
    product_id,
    SUM(CASE 
        WHEN action IN (1, 6) THEN 1     -- 新增赞 + 踩改赞 = likeCount +1
        WHEN action IN (3, 5) THEN -1    -- 取消赞 + 赞改踩 = likeCount -1
        ELSE 0 
    END) AS like_count,
    SUM(CASE 
        WHEN action IN (2, 5) THEN 1     -- 新增踩 + 赞改踩 = badCount +1
        WHEN action IN (4, 6) THEN -1    -- 取消踩 + 踩改赞 = badCount -1
        ELSE 0 
    END) AS bad_count
FROM t_review_log
GROUP BY product_id
HAVING like_count != 0 OR bad_count != 0;
```

然后用此结果批量写回 Redis Hash。

---

## 六、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| log 表无限增长 | 中 | 后续可加按月分区 / 冷热数据分层 |
| 同步写 log 增加 vote() 耗时 | 低 | 单 INSERT ~1-2ms，可接受 |
| 写 log 失败导致 vote 失败 | 中 | 与 DB 主操作同事务，回滚保证一致 |
| log 表与 t_review 不一致 | 低 | 同一事务内写，要么都成功要么都失败 |

---

## 七、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| #PROD-VOTE-008 | ✅ 已完成 | 本工单**依赖** #008 的 6 状态机 |
| **#PROD-VOTE-009**（本工单）| 待执行 | **下一步执行** |
| #PROD-VOTE-010 | 待规划 | **后续执行**：DB 写入改异步 |

**先执行 #009（加 log 表），再做 #010（DB 改异步）。两步不可合并**，因为 #010 的"异步写 DB"依赖 #009 的"log 表作为最终落库点"。

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 联调验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会按 9 种状态转移场景验证 log 表写入。**