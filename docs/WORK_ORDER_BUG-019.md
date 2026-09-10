---

# 工单 #BUG-019：OrderHistoryTool / OrderDetailTool ThreadLocal 跨线程 403 修复

> **创建时间**：2026-09-08
> **优先级**：🟠 **P1**（订单查询核心功能不可用）
> **接收方**：后端工程师
> **预计工时**：45 分钟
> **依赖**：
>   - ✅ #BUG-012（SearchDishesTool 已修复，但 OrderHistory/OrderDetail 未修复）
>   - ✅ #BUG-015/016/017/018（前置 SSE/Prompt/订单查询已修复）
>   - ✅ del-order SecurityConfig 已放行 `/internal/**` 给 INTERNAL_SERVICE 角色

---

## 一、问题陈述

### 1.1 用户反馈

用户问："我最近的一笔订单吃的什么？"
账号：sakana / 123456

LLM 调了 OrderHistoryTool，但 Feign 返回 403 Forbidden：

```
feign.FeignException$Forbidden: [403] during [GET] to
[http://del-order/api/v1/user/orders?page=1&size=1]
[OrderFeignClient#getUserOrders(int,int)]: []

[OrderHistoryTool] error=[403] during [GET] to [http://del-order/api/v1/user/orders?page=1&size=1]
```

### 1.2 影响范围

| Tool端点状态 |                                       |
| --------- | ---------------------------------------- |
| SearchDishesTool | `/internal/search` | ✅ 正常（#BUG-012 已修） |
| **OrderHistoryTool** | **`/user/orders`** | ❌ **403** |
| **OrderDetailTool** | **`/by-order-no/{orderNo}`** | ❌ **403**（同样会失败） |

**所有订单相关查询功能不可用**。

---

## 二、根因（#BUG-012 残留）

### 2.1 完整调用链

```
1. 用户请求到达 del-cs（Tomcat NIO 线程，userId=2087088327483965445）
   ↓
2. AuthContextFilter 从 Authorization 头提取 JWT，写入 ThreadLocal
   AuthContext.setToken(jwt) ✓
   AuthContext.USER_ID = 2087088327483965445 ✓
   ↓
3. ChatController → ChatService.streamChat
   ↓
4. assistant.chat() → Langchain4j
   ↓
5. Qwen LLM 决定调 getUserOrderHistory 工具
   ↓
6. DashScope SDK 切到 OkHttp 线程池（线程名：liyuncs.com/...）
   ↓
7. OrderHistoryTool.getUserOrderHistory() 在 OkHttp 线程上执行
   ↓
8. AuthFeignRequestInterceptor 在 OkHttp 线程上读 ThreadLocal
   AuthContext.getToken() → null（**ThreadLocal 没跨线程传播！**）
   ↓
9. Feign 请求不带 Authorization 头
   ↓
10. del-order /user/orders → .anyRequest().authenticated() → 403
```

### 2.2 为什么 #BUG-012 没修干净

#BUG-012 只修了 SearchDishesTool（让它走 `/internal/search`），但忘了同步改 OrderHistoryTool 和 OrderDetailTool。

---

## 三、修复方案（架构师已决策 = 方案 A1）

### 3.1 决策点

| #决策点选择理由 |                          |                                          |
| ---------- | ------------------------ | ---------------------------------------- |
| **Q1 ThreadLocal 跨线程还是新增 /internal 端点？** | ✅ **新增 /internal 端点** | TransmittableThreadLocal 对 DashScope SDK 线程无效 |
| **Q2 /internal 端点要不要传 userId？** | ✅ **传 userId** | 订单是用户私有，必须知道查谁的 |
| **Q3 OrderDetailTool 也改吗？** | ✅ **改** | 同样会 403 |

### 3.2 改动清单（4 处文件）

| #文件改动类型 |                                       |
| -------- | ---------------------------------------- |
| 1 | `del-order/.../controllers/OrderController.java`：新增 2 个 internal 端点 |
| 2 | `del-cs/.../feign/OrderFeignClient.java`：新增 2 个 internal 方法 |
| 3 | `del-cs/.../service/tools/OrderHistoryTool.java`：改用 internal 端点 |
| 4 | `del-cs/.../service/tools/OrderDetailTool.java`：改用 internal 端点 |

---

## 四、详细代码改动

### 改动 1：`OrderController.java` 新增 internal 端点

**文件**：`E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\web\controllers\OrderController.java`

在文件末尾新增：

```java
/**
 * ★ BUG-019：内部服务调用端点（避开 ThreadLocal 跨线程问题）
 * 鉴权：INTERNAL_SERVICE 角色（通过 X-Internal-Service-Token 头）
 */
@GetMapping("/internal/orders")
public R<List<OrderVO>> internalGetOrders(
        @RequestParam Long userId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    OrderPageResp resp = orderService.getMyOrders(userId, null, page, size);
    return R.ok(resp.getRecords());
}

@GetMapping("/internal/orders/by-order-no")
public R<OrderVO> internalGetByOrderNo(
        @RequestParam Long userId,
        @RequestParam String orderNo) {
    OrderVO order = orderService.getByOrderNo(userId, orderNo);
    return order != null ? R.ok(order) : R.fail(404, "订单不存在");
}
```

### 改动 2：`OrderFeignClient.java` 新增 internal 方法

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\feign\OrderFeignClient.java`

```java
@FeignClient(name = "del-order", contextId = "csOrderFeignClient", path = "/api/v1")
public interface OrderFeignClient {

    @GetMapping("/user/orders")
    R<List<OrderVO>> getUserOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * ★ BUG-019：通过 orderNo 查询（用户私有）
     */
    @GetMapping("/by-order-no/{orderNo}")
    R<OrderVO> getOrderDetail(@PathVariable("orderNo") String orderNo);

    // ★ BUG-019 新增：internal 端点（避开 ThreadLocal 跨线程问题）
    @GetMapping("/internal/orders")
    R<List<OrderVO>> getInternalUserOrders(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size);

    @GetMapping("/internal/orders/by-order-no")
    R<OrderVO> getInternalOrderByOrderNo(
            @RequestParam Long userId,
            @RequestParam String orderNo);
}
```

### 改动 3：`OrderHistoryTool.java`

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderHistoryTool.java`

```java
package com.sakana.cs.service.tools;

import com.sakana.cs.context.AuthContext;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryTool {
    private final OrderFeignClient orderFeign;

    @Tool("查询当前用户的订单历史列表。")
    public List<OrderVO> getUserOrderHistory(
            @P("要查询的订单数量") int maxResults) {
        log.info("[OrderHistoryTool] maxResults={}", maxResults);
        try {
            // ★ BUG-019：从 AuthContext 取 userId（ThreadLocal）
            String userIdStr = AuthContext.getUserId();
            if ("anonymous".equals(userIdStr)) {
                log.warn("[OrderHistoryTool] userId is anonymous");
                return List.of();
            }
            Long userId = Long.parseLong(userIdStr);
            // ★ 改用 internal 端点，避开 ThreadLocal 跨线程问题
            R<List<OrderVO>> resp = orderFeign.getInternalUserOrders(userId, 1, maxResults);
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.error("[OrderHistoryTool] error={}", e.getMessage(), e);
        }
        return List.of();
    }
}
```

### 改动 4：`OrderDetailTool.java`

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderDetailTool.java`

```java
package com.sakana.cs.service.tools;

import com.sakana.cs.context.AuthContext;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDetailTool {
    private final OrderFeignClient orderFeign;

    @Tool("查询指定订单的详细信息。orderNo 是业务订单号（以 ORD 开头的字符串，如 ORD20260904TEST_REVIEW_03）")
    public Optional<OrderVO> getOrderDetail(
            @P("业务订单号（如 ORD20260904TEST_REVIEW_03）") String orderNo) {
        log.info("[OrderDetailTool] orderNo={}", orderNo);
        try {
            // ★ BUG-019：从 AuthContext 取 userId
            String userIdStr = AuthContext.getUserId();
            if ("anonymous".equals(userIdStr)) {
                log.warn("[OrderDetailTool] userId is anonymous");
                return Optional.empty();
            }
            Long userId = Long.parseLong(userIdStr);
            // ★ 改用 internal 端点
            R<OrderVO> resp = orderFeign.getInternalOrderByOrderNo(userId, orderNo);
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

## 五、验证步骤

### 5.1 del-order 验证

```powershell
# 重启 del-order

# curl 直接验证新接口
$userId = "2087088327483965445"  # sakana 的 userId
$token = "internal-service-secret-key-2024"

curl.exe -i -X GET "http://localhost:10010/api/v1/internal/orders?userId=$userId&page=1&size=5" ^
  -H "X-Internal-Service-Token: $token"

# 预期：200 + 订单列表
```

### 5.2 del-cs 端到端验证

```powershell
# 重启 del-cs

# 测试 1：订单历史
$body = '{"message":"我最近的一笔订单吃的什么？"}'
curl.exe -s -X POST "http://localhost:10011/api/v1/cs/chat" ^
  -H "Authorization: Bearer <sakana_jwt_token>" ^
  -H "Content-Type: application/json" ^
  -d $body --max-time 30 | Select-String "type"

# 预期：tool 事件 + token 事件，无 error

# 测试 2：订单详情
$body = '{"message":"我订单 ORDxxx 详情"}'
# ... 同上
```

### 5.3 验收标准

| #测试问题预期 |                          |
| ---- | ---------------------------- |
| 1 | "我最近的一笔订单吃的什么？" | ✅ 返回真实订单列表 |
| 2 | "我最近的 5 条订单" | ✅ 返回 5 条订单 |
| 3 | "我订单 ORDxxx 详情" | ✅ 返回真实订单详情 |
| 4 | "我订单 ORD-not-exist 详情" | ✅ 返回"未找到"（404） |
| 5 | 问订单时 **不应该** 报 403 | ✅ |

### 5.4 关键日志

后端 del-cs 日志应包含：
```
[OrderHistoryTool] maxResults=X
[OrderHistoryTool] resp=...
[ChatService] Tool 执行: name=getUserOrderHistory
```

不应该再有：
```
❌ [OrderHistoryTool] error=[403]
❌ FeignException$Forbidden
```

---

## 六、风险与回滚

| 风险应对 |                                       |
| ---- | ------------------------------------- |
| AuthContext.getUserId() 返回 "anonymous" 时 | 🟢 返回空列表（不报错） |
| userId 解析失败（Long.parseLong 异常） | 🟡 当前 catch 块捕获，返回空列表 |

**回滚方案**：恢复 4 处改动到当前 commit。

---

## 七、关联工单

- **#BUG-012**：SearchDishesTool ThreadLocal 修复（已关闭）
- **#BUG-015/016/017/018**：已完成
- **#BUG-020（建议）**：根治 ThreadLocal 跨线程（TransmittableThreadLocal 或自定义 ToolExecutionHook）
