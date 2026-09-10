# 📋 工单 #P2-004：管理员面板接入 t_review_log 真实数据可视化

> **创建时间**：2026-09-04
> **优先级**：P2（业务能力提升，非阻塞）
> **接收方**：后端工程师 + 前端工程师
> **影响服务**：del-product（API）+ del-stats（聚合，可选）
> **预计工时**：后端 3h + 前端 4h
> **依赖工单**：#PROD-VOTE-009（已完成，t_review_log 表已建）

---

## 一、问题描述

### 1.1 背景

经过 #PROD-VOTE-009 + #010，**`t_review_log` 表已建好并持续写入**，里面保存了完整的评价事件历史：

```sql
SELECT action, old_action, event_time, ... FROM t_review_log;
```

但**当前没有任何前端页面消费这些数据**。管理员面板只是显示 Redis 的实时聚合数，看不到：

| 缺失数据 | 业务价值 |
|---------|---------|
| 历史趋势（点赞/踩的时间分布）| 运营决策 |
| 取消率（点了又取消的比例）| 产品健康度 |
| TOP10 商品（按点赞数）| 选品参考 |
| 活跃用户（按操作频次）| 用户运营 |
| 反复横跳用户 | 异常行为监控 |

### 1.2 目标

为管理员面板增加"评价数据洞察"页面，提供基于 `t_review_log` 的真实可视化。

---

## 二、修复方案

### 2.1 后端 API 设计

在 `del-product` 模块新增 `AdminReviewStatsController`：

```java
@RestController
@RequestMapping("/api/v1/admin/reviews/stats")
public class AdminReviewStatsController {
    
    @GetMapping("/trend")
    public R<List<TrendPointVO>> getTrend(
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) { ... }
    
    @GetMapping("/cancel-rate")
    public R<CancelRateVO> getCancelRate(...) { ... }
    
    @GetMapping("/top-products")
    public R<List<ProductRankVO>> getTopProducts(
        @RequestParam(defaultValue = "10") int limit
    ) { ... }
    
    @GetMapping("/active-users")
    public R<List<UserActivityVO>> getActiveUsers(...) { ... }
    
    @GetMapping("/flip-flop-users")
    public R<List<FlipFlopVO>> getFlipFlopUsers(...) { ... }
}
```

### 2.2 数据访问层

新建 `ReviewLogStatsMapper.xml`（或用注解）：

```java
public interface ReviewLogStatsMapper {
    // 趋势：每日点赞/踩/取消的数量
    @Select("""
        SELECT DATE(event_time) AS date,
               SUM(CASE WHEN action IN (1, 6) THEN 1 ELSE 0 END) AS like_count,
               SUM(CASE WHEN action IN (2, 5) THEN 1 ELSE 0 END) AS bad_count,
               SUM(CASE WHEN action IN (3, 4) THEN 1 ELSE 0 END) AS cancel_count
        FROM t_review_log
        WHERE event_time BETWEEN #{start} AND #{end}
        GROUP BY DATE(event_time)
        ORDER BY date
    """)
    List<TrendPointVO> selectTrend(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
    
    // 取消率
    @Select("""
        SELECT 
            COUNT(*) AS total,
            SUM(CASE WHEN action IN (3, 4) THEN 1 ELSE 0 END) AS cancelled
        FROM t_review_log
    """)
    CancelRateVO selectCancelRate();
    
    // TOP10 商品
    @Select("""
        SELECT product_id,
               SUM(CASE WHEN action IN (1, 6) THEN 1 WHEN action IN (3, 5) THEN -1 ELSE 0 END) AS net_likes
        FROM t_review_log
        GROUP BY product_id
        ORDER BY net_likes DESC
        LIMIT #{limit}
    """)
    List<ProductRankVO> selectTopProducts(@Param("limit") int limit);
    
    // ... 类似
}
```

### 2.3 前端可视化

新建 `src/views/admin/ReviewAnalyticsView.vue`：

- **顶部 KPI 卡片**：总点赞、总点踩、取消率、活跃用户数
- **折线图**：近 7/30 天点赞/踩/取消趋势（echarts 或 chart.js）
- **TOP 排行表格**：TOP10 商品、TOP10 活跃用户
- **异常行为**：反复横跳用户列表

### 2.4 路由与导航

`src/router/index.ts`：
```typescript
{
  path: '/admin/review-analytics',
  component: () => import('@/views/admin/ReviewAnalyticsView.vue'),
  meta: { title: '评价数据洞察', icon: '...' }
}
```

侧边栏 AdminLayout 增加入口。

---

## 三、具体改动清单

### 后端

| 文件 | 操作 | 行数 |
|------|------|------|
| `del-product/.../review/web/controllers/admin/AdminReviewStatsController.java` | 新建 | +80 |
| `del-product/.../review/dao/mapper/ReviewLogStatsMapper.java` | 新建 | +100 |
| `del-product/.../review/web/vo/TrendPointVO.java` | 新建 | +20 |
| `del-product/.../review/web/vo/CancelRateVO.java` | 新建 | +15 |
| `del-product/.../review/web/vo/ProductRankVO.java` | 新建 | +15 |
| `del-product/.../review/web/vo/UserActivityVO.java` | 新建 | +15 |
| `del-product/.../review/web/vo/FlipFlopVO.java` | 新建 | +15 |

### 前端

| 文件 | 操作 | 行数 |
|------|------|------|
| `src/views/admin/ReviewAnalyticsView.vue` | 新建 | +300 |
| `src/api/admin/reviewStats.ts` | 新建 | +50 |
| `src/router/index.ts` | 修改（新增路由）| +5 |
| `src/views/admin/AdminLayout.vue` | 修改（侧边栏新增菜单）| +10 |
| `package.json` | 视情况加 echarts 依赖 | +1 |

---

## 四、验收清单

### 4.1 后端

- [ ] 5 个接口全部实现
- [ ] 接口压测：trend 在 100 万 log 记录下查询 < 500ms
- [ ] 加合适索引（如果还没建）

### 4.2 前端

- [ ] 页面布局清晰
- [ ] 4 个可视化模块齐全（KPI/趋势/TOP/异常）
- [ ] 响应式适配
- [ ] 加载状态、错误状态、空状态都有

### 4.3 性能

- [ ] 趋势查询有索引支撑（idx_event_time）
- [ ] TOP 商品查询走 idx_log_product_time
- [ ] 大数据量下不卡顿（建议加缓存或物化视图）

---

## 五、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待填_ | |
| 前端执行 | _待填_ | |
| 验收完成 | _待填_ | |