

---

# 工单 #BUG-011：del-cs 路由未配置到 Nacos，导致 404

> **创建时间**：2026-09-08
> **优先级**：🔴 **P0**（阻塞 LLM 智能客服端到端使用）
> **接收方**：运维工程师
> **报告人**：运维工程师
> **根因定位**：运维工程师（三段式推理）
> **预计工时**：5 分钟

---

## 一、问题描述

前端调用 `/api/v1/cs/chat` 返回 **HTTP 404**，但 `del-cs` 的 CsApplication 日志**完全没有任何记录**。

### 证据链（运维工程师提供）

| 现象 | 结论 |
|------|------|
| 前端返回 404 | 请求没到 del-cs |
| CsApplication 无任何日志 | 请求没进 del-cs |
| 本地 `del-gateway.yml` 含 `/api/v1/cs/**` | 本地文件 OK |
| Nacos 上 `del-gateway.yml` 仍是旧版 | **Nacos 没更新** |

## 二、根因（运维工程师已定位）

`del-gateway.yml` 在**本地文件**添加了 `del-cs` 路由，但**没推送到 Nacos**：
- OpenAPI 推送 → 404 不通
- 手动 Nacos 控制台导入 → 没执行

Spring Cloud Gateway 路由表来自 Nacos 配置中心，本地文件改了没用。

## 三、修复方案

### 方案 A：Nacos 控制台手动导入（推荐，2 分钟）

1. 浏览器打开 `http://127.0.0.1:8848/nacos/`
2. 切换 namespace 到 `sakana`
3. 菜单：**配置管理 → 配置列表**
4. 找到 `del-gateway.yml`（Group: DEFAULT_GROUP）
5. 点击"编辑"
6. 在 `routes` 列表里追加：

```yaml
- id: del-cs
  uri: lb://del-cs
  predicates:
    - Path=/api/v1/cs/**
  filters:
    - StripPrefix=0
```

7. 点击"发布"

### 方案 B：直接改 application.yml（绕开 Nacos）

如果方案 A 紧急，先改 gateway 本地配置顶上去：

**文件**：`del-gateway/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ... 现有路由 ...
        - id: del-cs
          uri: lb://del-cs
          predicates:
            - Path=/api/v1/cs/**
          filters:
            - StripPrefix=0
```

⚠️ 缺点：gateway 重启会丢这个本地配置。

## 四、关键步骤：重启 gateway

Nacos 改了之后，**Spring Cloud Gateway 默认不热重载路由**。必须：

```powershell
# 路径 A：IDEA 重启 del-gateway（最稳）
# 路径 B：调 actuator refresh（如果配置了）
Invoke-WebRequest -X POST "http://localhost:10010/actuator/gateway/refresh" -UseBasicParsing
```

## 五、顺手修一个 bug

**`ChatController.chat` 的 `userId` 默认值问题**：

```java
String userId = body.getOrDefault("userId", "user-default");
//                                              ^^^^^^^^^^^^^^^^
//                                              永远走这里，因为前端从来不传 userId
```

**前端实际发**：
```typescript
body: JSON.stringify({ message, sessionId })  // 没 userId
```

**后果**：所有用户共享一个会话记忆。

**修复方案（推荐改后端，更鲁棒）**：

```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody Map<String, String> body, 
                       HttpServletRequest request) {
    String message = body.getOrDefault("message", "");
    if (message.isBlank()) {
        throw new IllegalArgumentException("message 不能为空");
    }
    
    // 从 AuthContext（由 AuthContextFilter 设入）取真实 userId
    String userId = AuthContext.getUserId();
    if (userId == null || userId.isBlank()) {
        userId = "anonymous-" + request.getSession().getId();
    }
    
    SseEmitter emitter = new SseEmitter(60_000L);
    // ... 其余不变
}
```

**修复位置**：`del-cs/src/main/java/com/sakana/cs/web/ChatController.java`

## 六、验证清单

### 6.1 网关路由验证

```bash
# 不带 token（验证路由通）
curl -X POST http://localhost:10010/api/v1/cs/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"测试"}'
# 期望：SSE 流（data: {"type":"token","content":"..."}...）

# 带 token（验证完整流程）
curl -X POST http://localhost:10010/api/v1/cs/chat \
  -H "Authorization: Bearer $C_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"推荐个清淡的汤"}'
# 期望：返回菜品推荐
```

### 6.2 del-cs 日志验证

```bash
# CsApplication 启动日志应该包含：
# - Tomcat initialized with port 10011
# - (Nacos 注册成功)

# 收到 chat 请求时应该出现：
# - [ChatController] 收到消息: ...
# - [ChatService] streamChat userId=...
```

### 6.3 前端 UI 验证

打开浏览器：
1. 登录 C 端用户
2. 点击右下角悬浮按钮
3. 输入"你好"
4. 看到 AI 流式回复
5. 输入"推荐清淡的汤"
6. 看到菜品列表
7. 关闭再打开，会话记忆仍在（用 AuthContext.getUserId() 取真实 userId）

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 根因定位 | 运维工程师 | 2026-09-08 ✅ |
| 报告生成 | 运维工程师 | 2026-09-08 ✅ |
| 工单生成 | Codex | 2026-09-08 |
| Nacos 配置 | _待运维_ | 5 分钟 |
| 重启 gateway | _待运维_ | 1 分钟 |
| ChatController userId bug | _待后端_ | 5 分钟 |
| 端到端验证 | _待验收_ | 10 分钟 |

---

## 八、给前端注意

修完后，前端**不用改任何代码**。ChatWidget 调用 `chatStreamFetch` 的方式：
```typescript
body: JSON.stringify({ message, sessionId })  // 没 userId
```

会工作，因为后端从 `AuthContext` 取真实 userId（通过 AuthContextFilter 自动从 `Authorization` header 解析）。

---

## 九、关联工单

| 工单 | 状态 |
|------|------|
| #AI-CS-001-MVP | ✅ 已完成（含 Phase 1 + Phase 2 + 前端）|
| #AI-CS-002-PHASE2 | ✅ 真实 LLM 接入 |
| **#BUG-011**（本工单）| 🔴 待修复 |
| #AI-CS-001 完整版 | ⏸️ 待立项 |

---

## 十、运维工程师的功劳

这次根因定位**非常专业**：

| 维度 | 评价 |
|------|------|
| **证据链完整性** | ✅ 从前端 → 网关 → 后端，逐层排除 |
| **根因定位准确度** | ✅ 网关路由缺失 + Nacos 未同步 |
| **额外发现** | ✅ userId 默认值 bug（顺手发现）|
| **方案可执行性** | ✅ 3 种修复方案 + 完整验证步骤 |
| **运维纪律** | ✅ 没有"试一下"，而是按 6 个候选逐个排除 |

这种**结构化调试思维**应该成为团队标准。运维的"问题分析报告"是工程师应该学习的范本。
