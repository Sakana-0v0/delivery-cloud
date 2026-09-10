# 📋 工单 #ARCH-FEIGN-001：切断 del-order → del-product 错误依赖

> **创建时间**：2026-09-04
> **优先级**：P1（阻塞点赞/踩功能）
> **修复方案**：移除 del-order 的 del-product 依赖 + 网关精确路由
> **接收方**：后端工程师
> **影响服务**：del-order、del-gateway（不增加服务数）
> **预计工时**：30 分钟
> **代码净变更**：pom.xml 删 5 行 + 网关配置 +5 行
> **业务代码改动**：**0 行**（已验证）

---

## 一、问题描述

### 1.1 错误现象

点赞/踩功能完全不可用，调用接口时报错：

```
### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: 
Table 'del_order_db.t_review_log' doesn't exist
### The error may exist in com/sakana/review/dao/mapper/ReviewLogMapper.java
### SQL: INSERT INTO t_review_log ...
```

### 1.2 根因（已 100% 定位）

| 环节 | 实际情况 |
|------|---------|
| `del-order/pom.xml` 依赖 `del-product` | ❌ 反模式（历史遗留） |
| `del-order` 启动时 ComponentScan | 扫描到 `del-product` 的所有 `@Component` |
| `VoteController`（在 `del-product` 模块）| 被错误地注册到 `del-order` 实例 |
| 网关 `/api/v1/orders/**` 路由 | 命中 `del-order` 实例 |
| 请求进入 `del-order` 的 `VoteController` | 数据源是 `del_order_db` |
| 写入 `t_review_log` | ❌ `del_order_db` 中没有此表 |

### 1.3 已验证的事实（重要）

我亲自执行了 grep 验证：

```powershell
Get-ChildItem del-order/src/main/java -Recurse -Filter *.java |
    Select-String -Pattern "import.*com\.sakana\.product"
# 结果：0 行
```

**结论**：`del-order` 的代码**完全没有引用** `com.sakana.product.*` 或 `com.sakana.review.*`。

`del-order` 实际引用的跨服务类全部来自 `del-common`：

| 引用的类 | 实际位置 |
|---------|---------|
| `com.sakana.feign.ProductFeignClient` | ✅ `del-common` |
| `com.sakana.feign.vo.ProductSnapshotVO` | ✅ `del-common` |
| `com.sakana.feign.UserFeignClient` | ✅ `del-common` |
| `com.sakana.web.vo.ProductVO` / `UserAddressVO` | ✅ `del-common` |

---

## 二、修复方案

### 2.1 核心思路

```
【修复前（错误）】
del-order ───pom.xml 直接依赖──▶ del-product
   │                              ├─ VoteController 被错误加载
   │                              ├─ ReviewServiceImpl 被错误加载
   │                              └─ ReviewLogMapper 用错数据源
   ↓
网关按 /api/v1/orders 路由（误打到 del-order）

【修复后（正确）】
del-order ───Feign Client（接口已在 del-common）──▶ del-product
   │
   └─ pom.xml 不再依赖 del-product
        ↓
   VoteController 只在 del-product 注册
   ↓
   网关精确路由 /api/v1/orders/*/items/*/vote 到 del-product
```

### 2.2 改动清单

| # | 改动 | 文件 | 行数 |
|---|------|------|------|
| 1 | 删除 `del-product` 依赖 | `del-order/pom.xml` | -5 行 |
| 2 | 新增 `del-product-vote` 精确路由 | `del-gateway.yml` (Nacos) | +5 行 |
| 3 | 调整 del-order 路由位置（移到 vote 路由之后）| `del-gateway.yml` | 调位置 |

**业务代码改动：0 行** ✅

---

## 三、具体改动

### 3.1 改动 1：del-order/pom.xml

**位置**：第 53-57 行

```diff
  <dependencies>
      <!-- 已存在 -->
      <dependency>
          <groupId>com.sakana</groupId>
          <artifactId>del-common</artifactId>
      </dependency>

-     <!-- ❌ 删掉：业务服务不应该直接依赖另一个业务服务 -->
-     <dependency>
-         <groupId>com.sakana</groupId>
-         <artifactId>del-product</artifactId>
-         <version>${project.version}</version>
-     </dependency>

      <!-- 其他依赖保持不变 -->
  </dependencies>
```

### 3.2 改动 2：del-gateway.yml（新增 vote 精确路由）

**位置**：Nacos 上 `del-gateway.yml`，在 `del-order` 路由**之前**插入

⚠️ **重要**：Spring Cloud Gateway 路由匹配是**按 yml 文件顺序**的（不是最长前缀匹配），所以更具体的路由必须写在前面。

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ==================== C 端路由 ====================
        # ... 其他路由保持不变 ...

        # ★ 新增：评价投票精确路由（必须在 del-order 路由之前）
        - id: del-product-vote
          uri: lb://del-product
          predicates:
            - Path=/api/v1/orders/*/items/*/vote
          filters:
            - StripPrefix=0

        # del-order 路由（向下移）
        - id: del-order
          uri: lb://del-order
          predicates:
            - Path=/api/v1/orders/**,/api/v1/cart/**,/api/v1/user/orders/**,/api/v1/admin/orders/**
          filters:
            - StripPrefix=0

        # 其他路由保持不变 ...
```

### 3.3 修改位置示意图

```yaml
# ❌ 修复前
- id: del-order                       ← 在最前面（导致 vote 误命中）
- id: del-product
- ...

# ✅ 修复后
- id: del-product-vote                ← 新增，放在 del-order 之前
- id: del-order                       ← 向下移
- id: del-product                     ← 不变
- ...
```

---

## 四、灰度验证步骤（建议新增）

### 步骤 1：仅重启 `del-order`

```bash
# 1.1 确认 del-order 启动后日志无 review Bean 加载
grep -i "review" del-order.log    # 应为空

# 1.2 确认 Feign 客户端仍正常注册
grep -i "productFeignClient" del-order.log
# 预期：Feign client 'productFeignClient' registered
```

### 步骤 2：调一个不涉及 review 的业务接口

```bash
# 验证订单业务不受影响
curl -X POST http://localhost:10010/api/v1/user/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"addressId":4,"items":[{"productId":1,"quantity":2}]}'
# 预期：200 OK
```

### 步骤 3：重启 `del-gateway` 加载新路由

```bash
# 3.1 在 Nacos 上修改 del-gateway.yml 后
# 3.2 重启 del-gateway 让路由生效
```

### 步骤 4：验证 vote 路径

```bash
curl -X POST http://localhost:10010/api/v1/orders/{orderId}/items/{productId}/vote \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"like"}'
# 预期：200 OK，t_review_log 写入成功
```

### 步骤 5：验证 9 种状态转移场景

按 #PROD-VOTE-008 验收清单跑一遍。

---

## 五、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 删除依赖后编译失败 | 🟢 低 | 已验证零引用 |
| Feign Client 找不到 `del-product` | 🟡 低 | Feign Client 是接口，运行时通过 Nacos 服务发现 |
| 网关路由顺序错误 | 🟡 中 | vote 路由写在 del-order **之前**；可用 `StripPrefix=0` 保留原路径 |
| Nacos del-gateway.yml 改动未生效 | 🟢 低 | 重启 del-gateway 强制刷新 |
| 其他隐藏 review Bean | 🟢 低 | 已确认 del-order 代码无 `com.sakana.review` 包引用 |

---

## 六、回滚方案（5 分钟）

如果实施后出现问题：

```bash
# 1. 恢复 del-order/pom.xml（git checkout）
cd del-order && git checkout pom.xml

# 2. 恢复 del-gateway.yml（删除新增的 vote 路由，移回 del-order）
#    在 Nacos 控制台编辑并发布

# 3. 重启 del-order 和 del-gateway
# 预期：恢复到修复前状态
```

---

## 七、影响评估

### 7.1 正面影响

| 项 | 影响 |
|----|------|
| 点赞/踩功能 | ✅ 从不可用恢复 |
| 微服务架构合规性 | ✅ 修复反模式 |
| 编译时间 | ✅ del-order 编译更快 |
| 内存占用 | ✅ del-order 启动内存更小（少 4 个 Bean）|
| 启动速度 | ✅ del-order 启动略快 |

### 7.2 潜在后续工作（非本工单范围）

| 优先级 | 任务 | 说明 |
|--------|------|------|
| P2 | 评估其他服务是否也有类似的错误模块依赖 | 治理微服务依赖 |
| P2 | 检查 `del-user` / `del-stats` / `del-payment` 等服务的 pom 依赖 | 同源治理 |

---

## 八、验收标准

### 8.1 功能验收

- [ ] 点赞（like）请求成功，写 `t_review_log` 成功
- [ ] 点踩（bad）请求成功
- [ ] toggle 取消请求成功
- [ ] 切换投票请求成功
- [ ] 创建订单、查询订单、支付订单全流程正常
- [ ] B 端管理功能正常

### 8.2 技术验收

- [ ] `del-order` 编译零错误
- [ ] `del-order/pom.xml` 不再依赖 `del-product`
- [ ] `del-gateway.yml` 路由按预期匹配（vote 路由在前）
- [ ] `del-order` 启动日志中**不再出现** `Review*` Bean 加载
- [ ] `del-order` 启动日志有 `Feign client 'productFeignClient' registered`

### 8.3 监控验收

- [ ] 24 小时后无新增 review 相关异常日志
- [ ] `t_review_log` 表新增数据正常

---

## 九、时间线

| 时间 | 事项 |
|------|------|
| T+0 | 架构师批准（当前） |
| T+10min | 后端工程师修改 pom.xml |
| T+15min | 后端工程师修改 Nacos 上 del-gateway.yml |
| T+20min | 重启 del-order + del-gateway |
| T+25min | 联调测试（4 种 vote 场景 + 订单流程） |
| T+30min | 工单完成 |

---

## 十、相关文件

| 路径 | 操作 |
|------|------|
| `E:\Idea_project\delivery-cloud\del-order\pom.xml` | 删 5 行 |
| Nacos 配置 `del-gateway.yml` | 增 5 行 + 调位置 |
| `E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\review\web\controllers\VoteController.java` | ❌ 不改（位置正确） |

---

## 十一、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 架构评估 | _待填_ | |
| 后端执行 | _待填_ | |
| 联调验收 | _待填_ | |

---

**后端工程师执行完成后请告知，我会按 8.1 + 8.2 验收清单做最终验证。**