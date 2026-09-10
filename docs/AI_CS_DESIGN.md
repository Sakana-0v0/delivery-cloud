# 🤖 AI 智能客服（LLM Customer Service）架构设计文档

> **版本**：v1.0
> **创建时间**：2026-09-05
> **状态**：📋 设计阶段（待立项）
> **设计依据**：`docs/WORK_ORDER_SEARCH-001.md`（前置）
> **预计启动时间**：SEARCH-001 完成后约 6 个月

---

## 一、背景与目标

### 1.1 业务需求

用户希望外卖系统提供**智能客服**功能，覆盖以下场景：

| 场景 | 说明 |
|------|------|
| 🍜 **菜品推荐** | "推荐清淡的汤"、"适合老人的软烂菜" |
| 🚚 **送餐咨询** | "我的订单到哪了？"、"还要多久能送到？" |
| 💰 **退款申请** | "我想退订这个订单"、"我的菜凉了能退款吗" |
| 📜 **订单历史** | "我上个月订了什么？"、"我常点的店有哪些？" |
| ⭐ **特色菜推荐** | "这家店有什么招牌菜？" |

### 1.2 设计目标

- ✅ 用户通过自然语言对话完成业务操作
- ✅ LLM 自动决定调用哪个后接口（Function Calling）
- ✅ 多轮对话支持上下文保持
- ✅ 响应时间：< 2s（首 token），< 10s（完整回答）
- ✅ 数据不出内网（合规）

### 1.3 与 SEARCH-001 的关系

**SEARCH-001 提供基础能力，本设计构建上层应用**：

```
SEARCH-001 (基础)
  ├─ ES dish 索引      ← LLM RAG 检索
  ├─ /api/v1/search    ← LLM Tool 调用
  ├─ EmbeddingClient   ← 抽象层，可选复用
  └─ Nacos 配置中心    ← LLM Prompt 也存这里

AI-CS (本设计)
  └─ 在 SEARCH-001 之上构建对话层
```

---

## 二、技术栈选型

### 2.1 决策：langchain4j ✅

| 选项 | langchain4j | Coze 平台 |
|------|------------|----------|
| 厂商 | Java 社区 | 字节跳动 |
| 形态 | 自部署 | SaaS |
| 工具调用 | Feign Client 自由调 | 插件市场受限 |
| LLM 选择 | DashScope / OpenAI / Qwen 任意 | 字节系 |
| 数据安全 | 内网 | 过字节云端 |
| 与现有架构契合 | ✅ Java 生态 | ❌ 第三方 SaaS |
| 集成内部 API | ✅ Feign 直接调 | ❌ 需要 webhook 桥接 |

**结论**：选 **langchain4j**（Java 版 LangChain），与现有 9 个 Java 微服务一致。

### 2.2 技术栈

| 维度 | 选型 |
|------|------|
| 服务框架 | Spring Boot 3.x |
| LLM 框架 | langchain4j |
| LLM 模型 | DashScope Qwen-Plus / Qwen-Max |
| 会话状态 | Redis（30 分钟过期）|
| 流式响应 | SSE（Server-Sent Events）|
| Prompt 管理 | Nacos 配置中心 |
| 服务发现 | Nacos |
| HTTP 客户端 | OpenFeign |

---

## 三、服务架构

### 3.1 服务定位

- **服务名**：`del-cs`（Customer Service）
- **端口**：10011
- **位置**：Java 微服务生态的第 10 个服务
- **特点**：AI 资源密集型，独立扩缩容

### 3.2 整体架构图

```
┌──────────────────────────────────────────────────────┐
│  [用户 WebSocket / SSE 客户端]                          │
│   小程序 / H5 / Web 聊天组件                            │
└────────────────────┬─────────────────────────────────┘
                     │ WebSocket / SSE
                     ▼
┌──────────────────────────────────────────────────────┐
│  del-cs (Java + langchain4j)                          │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │ ChatController                                │   │
│  │  POST /api/v1/cs/chat (SSE)                  │   │
│  │  WS  /api/v1/cs/ws                           │   │
│  └──────────────────────────────────────────────┘   │
│                     ↓                                │
│  ┌──────────────────────────────────────────────┐   │
│  │ ChatService (langchain4j Agent)              │   │
│  │  ├─ LLM: Qwen-Max                            │   │
│  │  ├─ Memory: RedisMessageStore              │   │
│  │  ├─ Tools: 8-10 个 @Tool 方法               │   │
│  │  └─ RAG: 复用 del-product /api/v1/search    │   │
│  └──────────────────────────────────────────────┘   │
│                     ↓                                │
│  ┌──────────────────────────────────────────────┐   │
│  │ Feign Clients (调用内部 API)                  │   │
│  │  ├─ ProductSearchFeignClient → del-product  │   │
│  │  ├─ OrderFeignClient → del-order            │   │
│  │  ├─ CartFeignClient → del-cart              │   │
│  │  └─ PaymentFeignClient → del-payment        │   │
│  └──────────────────────────────────────────────┘   │
│                     ↓                                │
│  ┌──────────────────────────────────────────────┐   │
│  │ Prompt Manager (从 Nacos 读)                  │   │
│  │  data-id: ai-cs-prompt.yml                  │   │
│  └──────────────────────────────────────────────┘   │
└──────────┬─────────────────┬────────────────────────┘
           │                 │
           ▼                 ▼
    ┌─────────────┐   ┌─────────────┐
    │ DashScope   │   │ Nacos       │
    │ Qwen-Max    │   │ (配置中心)   │
    └─────────────┘   └─────────────┘
           │
           │ Feign 调用
           ▼
    ┌────────────────────────────────────┐
    │  现有 9 个 Java 微服务（不动）       │
    │  del-product / del-order / del-cart │
    │  del-payment / del-user / ...      │
    └────────────────────────────────────┘
```

---

## 四、Tool 工具集设计

### 4.1 工具清单

| Tool 名称 | 调用方 | 功能 | 参数 |
|----------|--------|------|------|
| `searchDishes` | del-product | 语义搜索菜品 | query, maxResults |
| `getDishDetail` | del-product | 获取菜品详情 | dishId |
| `getOrderDetail` | del-order | 查询订单详情 | orderId |
| `getUserOrderHistory` | del-order | 查询用户历史订单 | days, status |
| `cancelOrder` | del-order | 取消订单 | orderId, reason |
| `createRefund` | del-order | 申请退款 | orderId, reason, items |
| `addToCart` | del-cart | 加入购物车 | productId, quantity |
| `createOrder` | del-order | 创建订单 | items, addressId |
| `getDeliveryStatus` | del-order | 查询配送状态 | orderId |
| `recommendDishes` | del-product | 特色菜推荐 | preference |

### 4.2 Tool 实现示例

```java
@Component
public class ProductTools {
    
    @Autowired
    private ProductSearchFeignClient searchClient;
    
    @Tool("语义搜索菜品，根据用户描述找到匹配的菜品")
    public List<ProductVO> searchDishes(
            @P("查询关键词") String query,
            @P("最大返回数量") int maxResults) {
        // 调用 del-product 的 /api/v1/search
        SearchResponse resp = searchClient.search(query, 1, maxResults);
        return resp.getItems();
    }
    
    @Tool("获取菜品详细信息（描述、价格、营养）")
    public ProductDetailVO getDishDetail(@P("菜品ID") Long dishId) {
        return productFeignClient.getDetail(dishId);
    }
}
```

### 4.3 Tool 调用流程（langchain4j 自动编排）

```
用户输入："推荐个清淡的汤"
    ↓
LLM 解析：意图=推荐，类型=清淡，类别=汤
    ↓
LLM 决定调用：searchDishes(query="清淡 汤")
    ↓
langchain4j 自动调用 @Tool 方法
    ↓
Feign 调用 del-product /api/v1/search
    ↓
返回 top-5 相关菜品
    ↓
LLM 收到结果，生成自然语言回答
    ↓
SSE 流式推送给用户："为您推荐番茄菠菜鸡蛋汤..."
```

---

## 五、会话状态管理

### 5.1 存储设计

```java
// Redis Key 设计
chat:session:{userId}     → Hash 存消息历史（30 分钟 TTL）
chat:context:{userId}     → String 存上下文摘要（订单ID等）
```

### 5.2 实现

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    
    @Autowired
    private StringRedisTemplate redis;
    
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = "chat:session:" + memoryId;
        List<Object> raw = redis.opsForList().range(key, 0, -1);
        // 反序列化为 ChatMessage
        return deserialize(raw);
    }
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = "chat:session:" + memoryId;
        redis.opsForList().rightPushAll(key, serialize(messages));
        redis.expire(key, Duration.ofMinutes(30));
    }
    
    @Override
    public void deleteMessages(Object memoryId) {
        redis.delete("chat:session:" + memoryId);
    }
}
```

---

## 六、流式响应（SSE）

### 6.1 接口设计

```java
@PostMapping(value = "/api/v1/cs/chat", produces = "text/event-stream")
public Flux<String> chat(@RequestBody ChatRequest req) {
    return chatService.streamChat(req);
}
```

### 6.2 实现

```java
@Service
public class ChatService {
    
    public Flux<String> streamChat(ChatRequest req) {
        // langchain4j StreamingChatLanguageModel
        return chatLanguageModel.generate(req.getMessages())
            .map(token -> "data: " + token + "\n\n");
    }
}
```

### 6.3 前端集成

```typescript
// src/api/com/chat.ts
export function chatWithAI(message: string, userId: string) {
  return fetch('/api/v1/cs/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, message })
  }).then(r => {
    const reader = r.body.getReader();
    // 逐 token 解析 SSE 流
  });
}
```

---

## 七、Prompt 管理

### 7.1 Nacos 配置

```
Data ID: ai-cs-prompt.yml
Group: DEFAULT_GROUP
```

```yaml
system_prompt: |
  你是一个外卖系统的智能客服助手，名叫"小饿"。
  你的职责是帮助用户：
  1. 推荐菜品（特色菜、按需求搜索）
  2. 查询订单状态和历史
  3. 申请退款
  4. 取消订单
  5. 引导下单

  行为规则：
  - 语气友好、简洁
  - 操作前先确认订单ID
  - 退款/取消订单前必须用户确认
  - 不知道的事情如实告知，不要瞎编

tool_usage_rules: |
  当用户表达模糊时（如"我想吃好吃的"）：
  1. 先用 searchDishes 搜索
  2. 根据结果推荐 3-5 个

  当用户说"我的订单"但没给订单号：
  1. 调用 getUserOrderHistory 列出最近订单
  2. 让用户选择

error_handling: |
  当工具调用失败时：
  - 不要告诉用户具体错误细节
  - 建议用户稍后重试或联系人工客服

fallback_response: |
  抱歉，我暂时无法处理您的请求。请稍后重试或输入"人工"转接人工客服。
```

### 7.2 热更新

- 修改 Nacos 配置 → 自动推送到所有 del-cs 实例
- 无需重启服务
- langchain4j 支持运行时 reload Prompt

---

## 八、Feign Client 定义

### 8.1 del-cs 内部 Feign Clients

```java
@FeignClient(name = "del-product", contextId = "productSearchFeign")
public interface ProductSearchFeignClient {
    @GetMapping("/api/v1/search")
    SearchResponse search(
        @RequestParam String query,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    );
}

@FeignClient(name = "del-order", contextId = "orderFeign")
public interface OrderFeignClient {
    @GetMapping("/api/v1/user/orders/{id}")
    OrderDetailVO getOrder(@PathVariable String id);
    
    @PutMapping("/api/v1/user/orders/{id}/cancel")
    R<Void> cancelOrder(@PathVariable String id);
    
    @PostMapping("/api/v1/user/orders/{id}/refund")
    RefundResultVO createRefund(
        @PathVariable String id,
        @RequestBody RefundReq req
    );
}
```

### 8.2 前置工单依赖

⚠️ **del-order 需要新增 `POST /api/v1/user/orders/{id}/refund` 退款接口**

这是 LLM 客服"退款申请"功能的前置条件，需要单独的工单 **#REFUND-001**。

---

## 九、安全与合规

### 9.1 鉴权

- 用户必须登录才能使用客服
- 复用现有 JWT 双 Token 池（USER_POOL）
- 每个请求验证 userId 与要操作的订单/资源是否属于当前用户

### 9.2 限流

```java
@Component
public class ChatRateLimiter {
    // 每个用户每分钟最多 10 次对话
    @RateLimiter(key = "chat:" + userId, limit = 10, window = "1m")
    public void checkLimit(String userId) { ... }
}
```

### 9.3 敏感操作二次确认

LLM 在执行以下操作前必须向用户确认：

- 取消订单
- 申请退款
- 修改收货地址
- 创建订单

---

## 十、性能与容量

### 10.1 性能指标

| 指标 | 目标 |
|------|------|
| 首 token 延迟 | < 2s |
| 完整回答延迟 | < 10s（ |
| 并发会话数 | 100（单实例）|
| 会话状态保留 | 30 分钟 |

### 10.2 资源估算

| 资源 | 估算 |
|------|------|
| CPU | 4 核（LLM 调用密集）|
| 内存 | 4GB |
| QPS 上限 | ~20 LLM calls/s |

---

## 十一、文件改动清单（实施时）

### 11.1 后端新建

| 文件 | 说明 |
|------|------|
| `del-cs/pom.xml` | 项目配置 |
| `del-cs/src/main/java/com/sakana/cs/CsApplication.java` | 启动类 |
| `del-cs/.../web/ChatController.java` | SSE / WebSocket 接口 |
| `del-cs/.../service/ChatService.java` | 主业务逻辑 |
| `del-cs/.../service/tools/ProductTools.java` | 菜品相关 Tool |
| `del-cs/.../service/tools/OrderTools.java` | 订单相关 Tool |
| `del-cs/.../service/tools/CartTools.java` | 购物车 Tool |
| `del-cs/.../service/tools/PaymentTools.java` | 支付 Tool |
| `del-cs/.../memory/RedisChatMemoryStore.java` | 会话存储 |
| `del-cs/.../feign/*FeignClient.java` | 多个 Feign Client |
| `del-cs/.../prompt/PromptManager.java` | Prompt 加载 |
| `del-cs/.../config/langchain4jConfig.java` | LLM 配置 |

### 11.2 前端新建

| 文件 | 说明 |
|------|------|
| `src/components/ChatWidget.vue` | 悬浮聊天组件 |
| `src/components/ChatMessage.vue` | 单条消息组件 |
| `src/api/cs/chat.ts` | API 封装 |
| `src/composables/useChat.ts` | 聊天 composable |

---

## 十二、风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| LLM 幻觉（瞎编信息）| 🟡 中 | 强制所有信息通过 Tool 获取，LLM 不能瞎说 |
| LLM 调用成本 | 🟡 中 | 限流、缓存常见回答 |
| LLM 拒答 | 🟢 低 | Prompt 明确告知能力边界 |
| Tool 调用失败 | 🟢 低 | 每个 Tool 有 try/catch + 降级提示 |
| SSE 长连接 | 🟢 低 | 30 分钟无活动自动关闭 |
| 用户隐私 | 🟡 中 | 不上传敏感字段到 LLM |

---

## 十三、启动条件

**必须满足以下条件才能立项 #AI-CS-001**：

- [ ] SEARCH-001 已完成并验收
- [ ] 菜品数据已索引到 ES
- [ ] C 端搜索可用
- [ ] del-order 新增退款接口（#REFUND-001）
- [ ] 业务方明确 LLM 客服需求优先级
- [ ] 团队准备投入 LLM 学习成本

---

## 十四、未来演进

### 14.1 Phase 3：AI 能力整合

如果未来要加更多 AI 功能（图像识别菜品、ASR 语音客服等）：

- 新建 `del-ai` 服务（Python）
- 统一所有 AI 能力
- embedding 从 Java 迁到 Python
- LLM 也迁到 Python

### 14.2 Phase 4：多模态

- 用户上传菜品图片 → LLM 识别 → 找同款
- 语音输入 → ASR → LLM 处理

---

## 十五、参考文档

- langchain4j 官方文档：https://docs.langchain4j.dev/
- Spring Boot SSE：https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- DashScope 文档：https://help.aliyun.com/zh/model-studio/

---

**版本历史**

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-09-05 | 初版设计 |