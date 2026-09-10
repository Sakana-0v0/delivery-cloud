# 📋 工单 #PROD-VOTE-006：多实例 Caffeine 缓存一致性（Pub/Sub 失效广播）

> **创建时间**：2026-09-04
> **优先级**：P1
> **触发条件**：多实例部署 `del-product`（生产环境必做）
> **修复方案**：Redis Pub/Sub 跨实例广播缓存失效
> **接收方**：后端工程师
> **影响服务**：`del-product`
> **预计工时**：1 小时
> **代码净变更**：+35 行（含新监听器类）

---

## 一、问题描述

### 1.1 错误现象

多实例部署下（生产环境典型场景），A 实例处理改投后，**只有 A 实例的本地 Caffeine 被清空**。B / C / D 实例的 Caffeine 仍有 stale value，导致：

```
时刻 T0：所有实例 Caffeine 都有 likeCount=1
时刻 T1：实例 A 处理改投 like→bad
  ├─ DB update ✓
  ├─ Redis Lua: likeCount=0, badCount=1  ✓ (所有实例共享 Redis)
  └─ invalidateLocal(123)  ← 只清 A 实例 Caffeine

时刻 T2：用户请求打到实例 B
  └─ getCount(123):
      L1 Caffeine (B 实例) 命中 likeCount=1  ← ❌ 旧值
      直接返回，永不查 Redis
```

### 1.2 根因

`ReviewCountCacheServiceImpl.invalidateLocal(Long productId)` 只清本实例 Caffeine：

```java
@Override
public void invalidateLocal(Long productId) {
    if (productId != null) {
        reviewCountCache.invalidate(productId);  // 仅本实例
        log.debug("[计数缓存] L1 失效: productId={}", productId);
    }
}
```

### 1.3 影响范围

| 场景 | 影响 |
|------|------|
| 单实例部署 | 无影响（Caffeine 与 Redis 一致） |
| 多实例 + 改投 | **严重**——其他实例显示错误计数 |
| 多实例 + 失效广播 | 解决 |

---

## 二、修复方案

### 2.1 方案核心：Redis Pub/Sub 广播失效

```
[实例 A] 改投完成
  ├─ DB update ✓
  ├─ Redis Lua ✓
  └─ publish "cache:invalidate" channel, msg="review:count:123"
       ↓ Redis Pub/Sub
  ┌────┴────┬────────┬────────┐
  ↓         ↓        ↓        ↓
[实例 A]  [实例 B]  [实例 C]  [实例 D]
本地 Caffeine invalidate  ✓      ✓      ✓      ✓
```

### 2.2 为什么选 Pub/Sub

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Redis Pub/Sub** ✅ | 实时、简单、Redis 自带、无额外组件 | 消息不持久（订阅者必须在线） |
| Redis Stream | 持久化、消费者组 | 复杂度高，本场景过度设计 |
| TTL 兜底 | 简单 | 数据会陈旧 TTL 时间 |
| 广播 + 定时补偿 | 容错强 | 复杂度高 |

**选择 Pub/Sub 的关键论据**：
- 缓存失效消息**不需要持久化**（丢了就丢了，下次写入会再次失效）
- 必须实时（用户期望立即看到新计数）
- 实现成本最低（Spring 已封装）

---

## 三、具体改动

### 3.1 修改/新增文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `del-product/src/main/java/com/sakana/review/services/ReviewCountCacheService.java` | 接口新增 `publishInvalidate(Long productId)` | +5 |
| `del-product/src/main/java/com/sakana/review/services/impl/ReviewCountCacheServiceImpl.java` | 实现 publish 方法 | +15 |
| `del-product/src/main/java/com/sakana/review/configs/CacheInvalidateListener.java` | **新建** - Pub/Sub 订阅监听器 | +25 |
| `del-product/src/main/java/com/sakana/review/services/impl/ReviewServiceImpl.java` | 改投分支调用 publish | +2 |

### 3.2 代码 Diff（关键片段）

#### 改动 1：`ReviewCountCacheService.java` 接口新增方法

```java
public interface ReviewCountCacheService {
    // ... 已有方法 ...

    /**
     * 跨实例广播缓存失效（通过 Redis Pub/Sub）
     * @param productId 商品 ID
     */
    void publishInvalidate(Long productId);
}
```

#### 改动 2：`ReviewCountCacheServiceImpl.java` 实现 publish

```java
private static final String INVALIDATE_CHANNEL = "cache:invalidate:review-count";

@Override
public void publishInvalidate(Long productId) {
    if (productId == null) {
        return;
    }
    try {
        stringRedisTemplate.convertAndSend(INVALIDATE_CHANNEL, String.valueOf(productId));
        log.debug("[计数缓存] 广播失效: productId={}", productId);
    } catch (Exception e) {
        log.warn("[计数缓存] 广播失效失败: productId={}, error={}", productId, e.getMessage());
    }
}
```

#### 改动 3（新建）：`CacheInvalidateListener.java`

```java
package com.sakana.review.configs;

import com.github.benmanes.caffeine.cache.Cache;
import com.sakana.review.web.vo.ReviewCountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 跨实例 Caffeine 缓存失效监听器
 *
 * <p>订阅 Redis Pub/Sub 频道 cache:invalidate:review-count，
 * 收到失效消息后清空本实例的本地 Caffeine 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidateListener implements MessageListener {

    private final Cache<Long, ReviewCountVO> reviewCountCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            Long productId = Long.parseLong(body);
            reviewCountCache.invalidate(productId);
            log.info("[缓存失效] 收到广播，失效 productId={}", productId);
        } catch (NumberFormatException e) {
            log.warn("[缓存失效] 收到非法消息: {}", body);
        }
    }
}
```

#### 改动 4：`ReviewServiceImpl.java` 改投分支追加 publish

```diff
  } else if (!oldVote.equals(type)) {
      Review r = new Review();
      r.setId(existing.getId());
      r.setType("like".equals(type) ? 1 : 2);
      reviewMapper.updateById(r);

      int deltaLike = "like".equals(type) ? 1 : -1;
      int deltaBad  = "bad".equals(type)  ? 1 : -1;
      reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
      reviewCountCacheService.invalidateLocal(productId);
+     // 跨实例广播失效（多实例部署时确保所有实例 Caffeine 失效）
+     reviewCountCacheService.publishInvalidate(productId);
  }
```

### 3.3 Spring 配置（如需要）

`StringRedisTemplate` 已有 `RedisMessageListenerContainer` 支持，**仅需在配置类注册订阅关系**：

```java
// 如已有 RedisConfig，可追加以下 bean
@Bean
public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory factory,
        CacheInvalidateListener listener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(listener, new ChannelTopic("cache:invalidate:review-count"));
    return container;
}
```

如果项目中已有类似的 `RedisConfig` 或 `RedisMessageListenerContainer` bean，**直接复用并追加订阅即可**。

---

## 四、验证清单

### 4.1 编译与启动

- [ ] `del-product` 模块编译成功
- [ ] 重启 `del-product` 服务（**建议至少部署 2 个实例测试**）
- [ ] 启动日志确认 `CacheInvalidateListener` 已注册：
  ```
  o.s.d.r.l.RedisMessageListenerContainer ... 
  ```

### 4.2 多实例失效测试

| # | 测试场景 | 预期 |
|---|---------|------|
| 1 | 实例 A 改投 like→bad | 5 个用户请求分别打到 A/B/C/D/E 实例，所有实例在 100ms 内都返回 likeCount=0, badCount=1 |
| 2 | 高频连续改投 | 同一 productId 连续 10 次改投，所有实例最终一致 |
| 3 | Redis Pub/Sub 断开 | 仅本实例清空，其他实例保持（已知限制） |

**测试方法**：
1. 启动 2 个 `del-product` 实例（端口不同，如 10006 和 10016）
2. 通过实例 A 改投
3. 立即在实例 B 上调用 `getCount`，验证返回新值

### 4.3 不回归验证

- [ ] 单实例部署下功能正常（向后兼容）
- [ ] 新增赞/踩（MQ 路径）不受影响
- [ ] 取消赞/踩（MQ 路径）不受影响
- [ ] Redis 不可用时不抛异常

---

## 五、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Pub/Sub 消息丢失 | 低 | 缓存失效消息丢了影响有限，下次写入会重新失效 |
| 订阅者不在线 | 中 | 单实例短暂陈旧，下次写入或读 miss 时自动恢复 |
| 大量无效广播 | 低 | 仅改投触发，频次极低 |
| 频道名冲突 | 低 | 频道名带业务前缀 `cache:invalidate:review-count` |

---

## 六、联调说明

| 工单 | 状态 | 关系 |
|------|------|------|
| #PROD-VOTE-005 | 待执行 | **依赖**（改投分支要先存在） |
| #PROD-VOTE-006（本工单） | 待执行 | 独立可执行，但需 #PROD-VOTE-005 先完成 |
| #PROD-VOTE-007 | 待规划 | 独立工单，不依赖本工单 |

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待后端工程师填写_ | |
| 多实例验收 | _待运维/测试工程师填写_ | |

---

**后端工程师执行完成后请告知，我会启动双实例做失效一致性验收。**