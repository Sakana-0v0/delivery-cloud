---

# 工单 #BUG-018：OrderDetailTool 类型不匹配导致订单查询失败

> **创建时间**：2026-09-08
> **优先级**：🟠 **P1**（订单查询核心功能不可用）
> **接收方**：后端工程师（del-order + del-cs）
> **预计工时**：1 小时（含 del-order 新增接口）
> **依赖**：
>   - ✅ #BUG-015（SSE 修复）
>   - ✅ #BUG-016（前端 SSE 匹配修复）
>   - ✅ #BUG-017（Prompt 强化）

---

## 一、问题陈述

### 1.1 用户反馈

用户问："ORD20260904TEST_REVIEW_03"

LLM 回复：
```
对不起，我没有找到订单号为 ORD20260904TEST_REVIEW_03 的相关信息。
可能是订单号输入有误或者是其他原因导致无法查询。
```

### 1.2 真根因（不是 LLM 问题，是 Tool 设计问题）

**3 处类型不匹配导致查询必然失败**：

| #位置代码问题 |                                       |
| ----- | ----------------------------------------- |
| 1 | `del-order/OrderController.getOrderDetail(@PathVariable Long id)` | 只接受 Long 数据库主键 |
| 2 | `del-cs/OrderFeignClient.getOrderDetail(@PathVariable("id") Long id)` | 同上 |
| 3 | `del-cs/OrderDetailTool.getOrderDetail(@P("订单ID") Long orderId)` | **致命**：要求 Long，但用户给的是 String "ORD..." |

### 1.3 数据库有两套订单标识

| 字段名类型用途 |                                       |
| ----- | ------------------------------------- |
| `id` | `Long`（数据库自增主键） | 系统内部用 |
| `orderNo` | `String`（业务订单号 "ORD..."） | **对用户展示** |

### 1.4 完整失败链

```
用户输入："ORD20260904TEST_REVIEW_03"（String 业务订单号）
   ↓
LLM 看到 Tool schema: orderId: Long
   ↓
LLM 试图转换 "ORD..." → Long → 失败
   ↓
LLM 传 null 或 0 给 Tool
   ↓
Feign: GET /user/orders/null
   ↓
后端查不到订单 → 返回 null
   ↓
LLM 收到空数据 → 回复"未找到订单"
```

---

## 二、根因分析

`OrderDetailTool` 设计错误：
- 用 Long orderId 调用 `/user/orders/{id}` 接口
- 但用户输入的是 String 业务订单号
- 两者无法匹配

**Prompt 与 Tool 不一致**：

当前 Prompt 说：
```
用户："我订单 ORD-123 详情" → 调 getOrderDetail(orderId="ORD-123")
```

但 Tool 参数是 Long，prompt 让 LLM 传 String。

---

## 三、修复方案（架构师已决策 = 方案 A）

### 3.1 决策点

| #决策点选择理由 |                          |                                          |
| ---------- | ------------------------ | ---------------------------------------- |
| **Q1 Tool 用 String orderNo 还是 Long id？** | ✅ **String orderNo** | 匹配用户实际输入（业务订单号） |
| **Q2 del-order 是改原接口还是新增？** | ✅ **新增 `/by-order-no/{orderNo}`** | 不破坏现有调用 |
| **Q3 是否删除原 `/{id}` 接口？** | ❌ **否** | 内部还在用，不破坏 |

### 3.2 改动清单（5 处）

| #文件改动类型 |                                       |
| -------- | ---------------------------------------- |
| 1 | `del-order/.../service/OrderService.java`：接口新增 `getByOrderNo(userId, orderNo)` |
| 2 | `del-order/.../service/impl/OrderServiceImpl.java`：实现新增方法 |
| 3 | `del-order/.../controllers/OrderController.java`：新增 `@GetMapping("/by-order-no/{orderNo}")` |
| 4 | `del-cs/.../feign/OrderFeignClient.java`：改用 String orderNo |
| 5 | `del-cs/.../service/tools/OrderDetailTool.java`：改用 String orderNo |

---

## 四、详细代码改动

### 改动 1：`OrderService.java` 接口

**文件**：`E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\services\OrderService.java`

```diff
 public interface OrderService extends IService<Order> {
     OrderPageResp getMyOrders(Long userId, Integer status, Integer page, Integer size);
     OrderVO getOrderDetail(Long userId, Long orderId);
+    OrderVO getByOrderNo(Long userId, String orderNo);  // ★ BUG-018
```

### 改动 2：`OrderServiceImpl.java` 实现

**文件**：`E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\services\impl\OrderServiceImpl.java`

在 `getOrderDetail` 方法后面新增：

```java
@Override
public OrderVO getByOrderNo(Long userId, String orderNo) {
    Order order = getOne(
            new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo)
                    .eq(Order::getUserId, userId)
    );
    if (order == null || order.getIsDeleted() == 1) {
        throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
    }
    List<OrderItem> orderItems = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
    return toVO(order, orderItems);
}
```

### 改动 3：`OrderController.java` 新增接口

**文件**：`E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\web\controllers\OrderController.java`

在 `getOrderDetail` 后面新增：

```java
/**
 * ★ BUG-018：通过业务订单号（orderNo）查询订单
 * 与 getOrderDetail(@PathVariable Long id) 区别：
 *   - id：数据库自增主键（Long）
 *   - orderNo：业务订单号（String，以 ORD 开头）
 */
@GetMapping("/by-order-no/{orderNo}")
public R<OrderVO> getByOrderNo(@PathVariable String orderNo) {
    OrderVO order = orderService.getByOrderNo(userId, orderNo);
    return order != null ? R.ok(order) : R.fail(404, "订单不存在");
}
```

### 改动 4：`OrderFeignClient.java`

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\feign\OrderFeignClient.java`

```diff
 @FeignClient(name = "del-order", contextId = "csOrderFeignClient", path = "/api/v1")
 public interface OrderFeignClient {
     @GetMapping("/user/orders")
     R<List<OrderVO>> getUserOrders(...);

-    @GetMapping("/user/orders/{id}")
-    R<OrderVO> getOrderDetail(@PathVariable("id") Long id);
+    /**
+     * ★ BUG-018：通过业务订单号查询
+     */
+    @GetMapping("/by-order-no/{orderNo}")
+    R<OrderVO> getOrderDetail(@PathVariable("orderNo") String orderNo);
 }
```

### 改动 5：`OrderDetailTool.java`

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderDetailTool.java`

```diff
 @Component
 @RequiredArgsConstructor
 public class OrderDetailTool {
     private final OrderFeignClient orderFeign;

-    @Tool("查询指定订单的详细信息。")
+    @Tool("查询指定订单的详细信息。orderNo 是业务订单号（以 ORD 开头的字符串，如 ORD20260904TEST_REVIEW_03）")
     public Optional<OrderVO> getOrderDetail(
-            @P("订单ID") Long orderId) {
-        log.info("[OrderDetailTool] orderId={}", orderId);
+            @P("业务订单号（如 ORD20260904TEST_REVIEW_03）") String orderNo) {
+        log.info("[OrderDetailTool] orderNo={}", orderNo);
         try {
-            R<OrderVO> resp = orderFeign.getOrderDetail(orderId);
+            R<OrderVO> resp = orderFeign.getOrderDetail(orderNo);
             if (resp != null && resp.getData() != null) {
                 return Optional.of(resp.getData());
             }
         } catch (Exception e) {
             log.error("[OrderDetailTool] error={}", e.getMessage(), e);
         }
         return Optional.empty();
     }
 }
```

---

## 五、Prompt 同步更新

**推送位置**：Nacos `del-cs-prompt.yml`（与 #BUG-017 一起推送）

把原来：
```
用户："我订单 ORD-123 详情" → 调 getOrderDetail(orderId="ORD-123")
```

改为：
```
用户："我订单 ORD-xxx 详情" → 调 getOrderDetail(orderNo="ORD-xxx")
```

---

## 六、验证步骤

### 6.1 后端 del-order 验证

```powershell
# 重启 del-order（修改了 Controller + Service）

# curl 直接验证新接口
$body = ''
$token = '<user_jwt_token>'

curl.exe -i -X GET "http://localhost:10011/del-order-url/api/v1/by-order-no/ORD20260904TEST_REVIEW_03" `
  -H "Authorization: Bearer $token"
```

实际上 del-order 端口可能不同，需要查一下。

### 6.2 del-cs 端到端验证

```powershell
# 重启 del-cs（修改了 OrderFeignClient + OrderDetailTool）

# curl 通过 del-cs 10011 测试
$body = '{"message":"我订单 ORD20260904TEST_REVIEW_03 详情"}'
curl.exe -s -X POST "http://localhost:10011/api/v1/cs/chat" `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d $body `
  --max-time 30 | Select-String "type"
```

**预期**：能看到 1 个 tool 事件 + 多个 token 事件，回复包含真实订单详情。

### 6.3 验收标准

| #测试问题预期 |                          |
| ---- | ---------------------------- |
| 1 | "我订单 ORD20260904TEST_REVIEW_03 详情" | ✅ 返回真实订单详情 |
| 2 | "我订单 ORD-xxx 不存在" | ✅ LLM 说"未找到" |
| 3 | "我最近的订单" | ✅ 返回订单列表（OrderHistoryTool 不受影响） |

### 6.4 关键日志

后端 del-cs 日志应包含：
```
[OrderDetailTool] orderNo=ORD20260904TEST_REVIEW_03
[OrderDetailTool] resp=...
[ChatService] Tool 执行: name=getOrderDetail
```

del-order 日志应包含：
```
[订单查询] userId=X, orderNo=ORD20260904TEST_REVIEW_03
```

---

## 七、风险与回滚

| 风险应对 |                                       |
| ---- | ------------------------------------- |
| 新接口 `/by-order-no/{orderNo}` 被滥用 | 🟢 需要 JWT 鉴权 + userId 校验，不影响 |
| 改 OrderFeignClient 路径影响其他调用 | 🟢 del-cs 是唯一调用方，影响范围可控 |

**回滚方案**：恢复 5 处改动到当前 commit。

---

## 八、关联工单

- **#BUG-017**：Prompt 强化（同步推送 Nacos）
- **#BUG-015**：SSE 修复（已关闭）
- **#BUG-016**：前端 SSE 匹配（已关闭）
