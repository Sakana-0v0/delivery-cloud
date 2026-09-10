---

# 工单 #BUG-020：订单工具 ThreadLocal 丢失 — userId 作为 @Tool 参数传递

> **创建时间**：2026-09-08
> **优先级**：🔴 **P0**（订单查询核心功能实际不可用）
> **接收方**：后端工程师
> **预计工时**：45 分钟
> **依赖**：
>   - ✅ #BUG-012（已尝试用 internal 端点解决 403，但没解决 ThreadLocal）
>   - ✅ #BUG-019（验收失误 — 工具实际短路返回空列表，需要回滚）

---

## 一、问题陈述

### 1.1 用户反馈

用户问："我最近的一笔订单吃的什么？"
账号：sakana / 123456

LLM 回复："无法获取订单信息"

### 1.2 #BUG-019 验收失误说明

#BUG-019 之前的"验收通过"是**假阳性**：
- ✅ tool 事件被触发
- ✅ 没有 403 错误
- ❌ **但工具内部 AuthContext.getUserId() 在异步线程返回 "anonymous"**
- ❌ **工具第一行就短路返回 List.of()，Feign 根本没调用**
- ❌ LLM 收到 "[]" → 告诉用户"找不到订单"

**真实情况**：订单查询功能一直不可用，只是表面看起来"工具调用了"。

### 1.3 完整调用链（暴露根因）

```
1. 用户请求到达 del-cs（Tomcat NIO 线程，userId=2087088327483965445）
   ↓
2. AuthContextFilter 从 Authorization 头提取 JWT
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
8. AuthContext.getUserId() 在 OkHttp 线程 → "anonymous"
   ↓
9. "anonymous".equals(userIdStr) → true → return List.of() ⚠️ 短路
   ↓
10. Feign 根本没被调用
   ↓
11. LLM 收到 "[]" → 回复"找不到订单"
```

---

## 二、根因

**AuthContext 是 ThreadLocal，HTTP 线程设置的值无法传播到 DashScope SDK 的 OkHttp 线程池**。

这是 #BUG-012 已经发现的问题。当时用 internal 端点绕过了 403 错误，但**没解决 userId 获取问题**。

### 2.1 为什么之前的修复都没真正解决

| #修复方案问题 |                                       |
| ---- | ------------------------------------- |
| #BUG-012 internal 端点 | ✅ 解决了 403（Feign 调用成功）<br>❌ 没解决 ThreadLocal（工具仍返回空） |
| #BUG-019 internal 端点 + AuthContext.getUserId() | ❌ AuthContext 在异步线程是 "anonymous"，工具短路返回 |

### 2.2 为什么不能继续用 ThreadLocal 方案

| 方案局限 |                                       |
| ---- | ------------------------------------- |
| InheritableThreadLocal | ❌ 只对子线程继承有效，DashScope SDK 用线程池复用线程，失效 |
| TransmittableThreadLocal + transmittable-thread-local 库 | ❌ 需要 JDK Agent 字节码织入，对 OkHttp 线程池仍不稳定 |
| 直接传 userId 参数 | ✅ **彻底解决，不依赖任何线程机制** |

---

## 三、修复方案（架构师已决策）

### 3.1 决策点

| #决策点选择理由 |                          |                                          |
| ---------- | ------------------------ | ---------------------------------------- |
| **Q1 继续 ThreadLocal 还是参数传递？** | ✅ **参数传递** | 彻底根除线程依赖 |
| **Q2 userId 怎么传给 LLM？** | ✅ **ChatController 加到消息前缀** | 后端注入，可靠性高 |
| **Q3 Feign 端点？** | ✅ **回到 /user/orders** | userId 来自 LLM（与 JWT 一致），JWT 鉴权生效 |
| **Q4 OrderFeignClient 怎么改？** | ✅ **回滚 #BUG-019 改动 + 加 userId 参数** | 保持单一鉴权路径 |

### 3.2 改动清单（5 个文件）

| #文件改动类型回滚/新增 |                                       |                                          |
| -------- | ---------------------------------------- | ---------------------------------------- |
| 1 | `ChatController.java` | **新增**：在 message 前加 userId 前缀 |
| 2 | `OrderHistoryTool.java` | **修改**：userId 从 ThreadLocal 改为 @Tool 参数 |
| 3 | `OrderDetailTool.java` | **修改**：userId 从 ThreadLocal 改为 @Tool 参数 |
| 4 | `OrderFeignClient.java` | **回滚 #BUG-019**：删 internal 方法 + 加 userId 参数 |
| 5 | Nacos `del-cs-prompt.yml` | **修改**：强调"消息开头有【当前用户ID: xxx】" |

---

## 四、详细代码改动

### 改动 1：ChatController.java（关键）

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\web\ChatController.java`

```diff
 @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 public SseEmitter chat(@RequestBody Map<String, String> body) {
     String message = body.getOrDefault("message", "");
     if (message.isBlank()) {
         throw new IllegalArgumentException("message 不能为空");
     }

     SseEmitter emitter = new SseEmitter(60_000L);
     String userId = com.sakana.cs.context.AuthContext.getUserId();
     log.info("[ChatController] 收到消息: userId={}, message={}", userId, message);

+    // ★ BUG-020：在消息前面拼接 userId 前缀
+    // 让 LLM 在异步线程中能从 UserMessage 提取 userId 传给 Tool
+    String enhancedMessage = String.format("【当前用户ID: %s】%s", userId, message);

     chatService.streamChat(
         userId,
-        message,
+        enhancedMessage,
         chunk -> { ... },
         v -> { ... },
         error -> { ... }
     );

     return emitter;
 }
```

**为什么这样改**：
- ChatController 在 HTTP 线程（有 AuthContext）拿到 userId
- 把 userId 加到 message 前面，构造 enhancedMessage
- Langchain4j 把 enhancedMessage 作为 UserMessage 传给 LLM
- LLM 在异步线程中能从 UserMessage 字符串里提取 userId（不依赖 ThreadLocal）

---

### 改动 2：OrderHistoryTool.java

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderHistoryTool.java`

**完整文件替换**：

```java
package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    /**
     * ★ BUG-020：userId 从 @Tool 参数传入（不再依赖 ThreadLocal）
     */
    @Tool("查询当前登录用户的订单历史列表。")
    public String getUserOrderHistory(
            @P("用户ID，从用户消息开头的【当前用户ID: xxx】中提取") String userId,
            @P("要查询的订单数量，默认5") Integer maxResults) {

        Long uid = parseUserId(userId);
        if (uid == null) {
            log.warn("[OrderHistoryTool] userId 无效: {}", userId);
            return "[]";
        }
        int size = (maxResults != null && maxResults > 0) ? maxResults : 5;
        log.info("[OrderHistoryTool] userId={}, maxResults={}", uid, size);

        try {
            R<OrderPageResp> resp = orderFeign.getUserOrders(uid, 1, size);
            if (resp != null && resp.getData() != null && resp.getData().getRecords() != null) {
                return objectMapper.writeValueAsString(resp.getData().getRecords());
            }
        } catch (JsonProcessingException e) {
            log.error("[OrderHistoryTool] JSON序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[OrderHistoryTool] error={}", e.getMessage(), e);
        }
        return "[]";
    }

    private Long parseUserId(String s) {
        if (s == null || s.isBlank() || "anonymous".equals(s)) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

---

### 改动 3：OrderDetailTool.java

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderDetailTool.java`

**完整文件替换**：

```java
package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDetailTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    /**
     * ★ BUG-020：userId 从 @Tool 参数传入（不再依赖 ThreadLocal）
     */
    @Tool("查询指定订单的详细信息。")
    public String getOrderDetail(
            @P("用户ID，从用户消息开头的【当前用户ID: xxx】中提取") String userId,
            @P("业务订单号（如 ORD20260904TEST_REVIEW_03）") String orderNo) {

        Long uid = parseUserId(userId);
        if (uid == null) {
            log.warn("[OrderDetailTool] userId 无效: {}", userId);
            return "null";
        }
        log.info("[OrderDetailTool] userId={}, orderNo={}", uid, orderNo);

        try {
            R<OrderVO> resp = orderFeign.getByOrderNo(uid, orderNo);
            if (resp != null && resp.getData() != null) {
                return objectMapper.writeValueAsString(resp.getData());
            }
        } catch (JsonProcessingException e) {
            log.error("[OrderDetailTool] JSON序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[OrderDetailTool] error={}", e.getMessage(), e);
        }
        return "null";
    }

    private Long parseUserId(String s) {
        if (s == null || s.isBlank() || "anonymous".equals(s)) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

---

### 改动 4：OrderFeignClient.java（回滚 #BUG-019）

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\feign\OrderFeignClient.java`

```diff
 @FeignClient(name = "del-order", contextId = "csOrderFeignClient", path = "/api/v1")
 public interface OrderFeignClient {

     @GetMapping("/user/orders")
-    R<List<OrderVO>> getUserOrders(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size);
+    // ★ BUG-020：加 userId 参数
+    R<OrderPageResp> getUserOrders(
+            @RequestParam Long userId,
+            @RequestParam(defaultValue = "1") int page,
+            @RequestParam(defaultValue = "20") int size);

     @GetMapping("/by-order-no/{orderNo}")
-    R<OrderVO> getOrderDetail(@PathVariable("orderNo") String orderNo);
+    // ★ BUG-020：加 userId 参数，改方法名 getByOrderNo
+    R<OrderVO> getByOrderNo(
+            @RequestParam Long userId,
+            @PathVariable String orderNo);
-
-    // ★ BUG-019 的 internal 端点已回滚
-    @GetMapping("/internal/orders")
-    R<List<OrderVO>> getInternalUserOrders(...);
-
-    @GetMapping("/internal/orders/by-order-no")
-    R<OrderVO> getInternalOrderByOrderNo(...);
 }
```

**注意**：
- 删掉 #BUG-019 加的 `getInternalUserOrders` 和 `getInternalOrderByOrderNo`
- 恢复原来的 `getUserOrders`，但加 `Long userId` 参数
- `getOrderDetail` 改名为 `getByOrderNo`（之前 #BUG-018 加的，现在统一），加 `Long userId` 参数
- 返回类型从 `R<List<OrderVO>>` 改为 `R<OrderPageResp>`（包含 records 字段）

⚠️ 如果 del-order 后端接口路径或参数不一致，需要同步修改 del-order 的 OrderController。请同步检查。

---

### 改动 5：Nacos del-cs-prompt.yml

**推送位置**：Nacos `sakana` namespace / `DEFAULT_GROUP` / `del-cs-prompt.yml`

```yaml
# del-cs 智能客服模块 Prompt 配置（#AI-CS-002-PHASE2 + #BUG-020 强化）
# 存在 Nacos：namespace=sakana / group=DEFAULT_GROUP / dataId=del-cs-prompt.yml

system_prompt: |
  你是外卖系统智能客服"小饿"。

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ⚠️⚠️⚠️ 强制规则（不可违反）⚠️⚠️⚠️
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  【用户消息格式】
  每条用户消息开头都包含"【当前用户ID: xxx】"标记（后端自动注入）。
  调订单工具时**必须**从这个标记提取 userId 传入，不能凭想象编造。

  1. **绝对禁止凭自己知识回答任何具体数据**
     - 菜品价格、热量、营养、库存 → 必须调 searchDishes 工具
     - 订单状态、订单详情、订单历史 → 必须调 getOrderDetail / getUserOrderHistory 工具
     - 你的训练数据可能过时，**只有工具返回的数据才是真实的**

  2. **任何涉及"具体菜品"或"具体订单"的问题，必须先调工具**
     - 即使问题看起来很简单，也必须先调工具
     - 不调工具就回答 = 编答案 = 错误行为

  3. **工具返回空列表 = 老实说没找到**

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  你的能力（只能做这三件事）
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. 推荐菜品：用 searchDishes(query) 工具
  2. 查询单个订单：用 getOrderDetail(userId, orderNo) 工具
     - userId 从消息开头【当前用户ID: xxx】提取
     - orderNo 用户会告诉你（如"我订单 ORDxxx 详情"）
  3. 查询订单历史：用 getUserOrderHistory(userId, maxResults) 工具
     - userId 从消息开头【当前用户ID: xxx】提取
     - maxResults 用户会告诉你（如"最近5笔"），默认5

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  调用示例
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  用户消息："【当前用户ID: 2087088327483965445】番茄鱼汤卡路里多少"
  → 调 searchDishes(query="番茄鱼汤")

  用户消息："【当前用户ID: 2087088327483965445】我最近一笔订单吃的什么"
  → 调 getUserOrderHistory(userId="2087088327483965445", maxResults=1)

  用户消息："【当前用户ID: 2087088327483965445】我订单 ORD20260904TEST_REVIEW_03 详情"
  → 调 getOrderDetail(userId="2087088327483965445", orderNo="ORD20260904TEST_REVIEW_03")

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  行为规则
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  - 不知道的事如实告知，不要瞎编
  - 简洁友好，回复不超过 100 字
  - 用户问退款/取消/下单/加购时，礼貌告知："抱歉，当前版本暂不支持此功能，建议联系人工客服"

fallback_response: |
  抱歉，我暂时无法处理您的请求。请稍后重试，或联系人工客服。

welcome_message: |
  你好！我是智能客服小饿 🍱
  我可以帮你：
  • 推荐菜品（比如"想吃点清淡的"）
  • 查询订单详情（"我的订单 ORD-xxx 怎么样了"）
  • 查看最近订单

  请告诉我你想做什么？
```

---

## 五、关键依赖同步检查

⚠️ **OrderFeignClient.java 的修改依赖于 del-order 后端接口实现**。请同步检查：

### del-order `OrderController.java` 是否需要改

```java
// 当前 #BUG-019 的 internal 端点（需要删除）
@GetMapping("/internal/orders")
public R<List<OrderVO>> internalGetOrders(...) { ... }

@GetMapping("/internal/orders/by-order-no")
public R<OrderVO> internalGetByOrderNo(...) { ... }
```

需要：
- **删除这两个 internal 端点**（不再需要）
- **恢复 `getUserOrders` 支持 userId 参数**（如果是按 userId 查询而不是 userId from JWT）

⚠️ **注意**：之前 OrderController 的 `getMyOrders(userId, ...)` 已经有 userId 参数，但要确认它是从 JWT 取的还是从 @RequestParam 取的。

如果是 JWT 取的，需要改成 @RequestParam Long userId。

如果已经是 @RequestParam，那就 OK。

---

## 六、验证步骤

### 6.1 后端 del-cs 验证

```powershell
# 1. 重启 del-cs（5 个文件全部改完）

# 2. curl 测试订单历史（关键验证：userId 不再是 anonymous）
$body = '{"message":"我最近一笔订单吃的什么"}'
curl.exe -s -X POST "http://localhost:10011/api/v1/cs/chat" ^
  -H "Authorization: Bearer <sakana_jwt_token>" ^
  -H "Content-Type: application/json" ^
  -d $body --max-time 30 | Select-String "type"

# 预期：tool 事件 + token 事件 + 最终有真实订单信息
```

### 6.2 验收标准

| #测试问题期望 |                                       |
| ---- | ---------------------------- |
| 1 | "我最近一笔订单吃的什么" | ✅ 返回真实订单（不再是"找不到"） |
| 2 | "我最近的 5 笔订单" | ✅ 返回 5 条订单 |
| 3 | "我订单 ORDxxx 详情" | ✅ 返回真实订单详情 |
| 4 | "我订单 不存在 详情" | ✅ 返回"未找到" |
| 5 | 后端日志 | ✅ 不再有 `[OrderHistoryTool] userId 无效` |

### 6.3 关键日志

后端 del-cs 日志应该看到：
```
[ChatController] 收到消息: userId=2087088327483965445, message=我最近一笔订单吃的什么
[OrderHistoryTool] userId=2087088327483965445, maxResults=1
[OrderHistoryTool] resp=...
[ChatService] Tool 执行: name=getUserOrderHistory
```

**不应该再有**：
```
❌ [OrderHistoryTool] userId 无效: anonymous
❌ [OrderHistoryTool] 无法获取用户ID
❌ FeignException$Forbidden
```

---

## 七、风险与回滚

### 风险

| 风险应对 |                                       |
| ---- | ------------------------------------- |
| LLM 提取 userId 错误 | 🟡 Prompt 强调 + Tool 内部 parseUserId() 兜底 |
| del-order 接口不匹配 | 🟡 需要后端工程师同步检查并修改 |
| AuthContext 仍可继续用 | 🟢 不删除，留给其他可能场景 |

### 回滚方案

恢复 5 个改动到当前 commit（#BUG-019 后的状态）。但 #BUG-019 的 internal 端点可以保留作为备用方案。

---

## 八、与 #BUG-019 / #BUG-019-FIX 的关系

| 工单状态处理 |                                       |
| ------------------- | -------------------------------------- |
| #BUG-019 | ⚠️ **撤销通过验收**（实际是假阳性，工具短路返回空） |
| #BUG-019-FIX | ⚠️ **撤销**（工单漏了 ChatController 改动） |
| **#BUG-020（本次）** | ✅ **真修复**（5 个文件完整改动） |

---

## 九、后续行动

1. **后端工程师**：
   - 应用 5 处改动
   - **先确认 del-order OrderController 接口**（是否需要改）
   - 重启 del-cs
   - curl 验证

2. **架构师**：
   - 推送 Nacos Prompt 到 del-cs-prompt.yml
   - 验证完整链路

3. **验收**：
   - 问订单相关问题，确认返回真实数据（不再是空）
