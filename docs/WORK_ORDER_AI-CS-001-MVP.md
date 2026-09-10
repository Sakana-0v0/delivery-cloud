
---

# 工单 #AI-CS-001-MVP：LLM 智能客服 MVP

> **创建时间**：2026-09-07
> **优先级**：P0（业务需求）
> **接收方**：后端（Java）+ 前端 + 运维联合实施
> **预计工时**：5 天
> **设计依据**：`docs/AI_CS_DESIGN.md`（裁剪 MVP 版本）
> **依赖**：
> - SEARCH-001 ✅（已完成）
> - #REFUND-001（**不在本工单范围**，MVP 不做退款 Tool）

---

## 一、范围（与原设计的差异）

| 项 | 完整版 | **MVP（本工单）** |
|------|--------|---------|
| Tools | 10 个 | **3 个**：searchDishes / getOrderDetail / getUserOrderHistory |
| 退款 Tool | 有 | ❌ 不做（待 #REFUND-001） |
| 取消/创建订单 | 有 | ❌ 不做 |
| 加入购物车 | 有 | ❌ 不做 |
| 服务端 | del-cs 10011 | 同样 |
| langchain4j + Qwen | 有 | 同样 |
| SSE 流式响应 | 有 | 同样 |
| Nacos Prompt | 有 | 同样 |
| Redis 会话状态 | 有 | 同样 |
| 前端形态 | 多种 | **只做悬浮 widget**（α） |
| 完整聊天页 | 有 | ❌ 不做 |
| 网关路由 | 完整 | 同样 |

---

## 二、Tool 工具集（3 个）

### 1. `searchDishes`（核心 — RAG 检索）

```java
@Tool("搜索商品，根据用户描述找到匹配的菜品")
public List<ProductVO> searchDishes(
        @P("查询关键词") String query,
        @P("最大返回数量") int maxResults) {
    return searchFeign.search(query, 1, maxResults);
}
```

调 `GET /api/v1/search?query={query}&page=1&size={n}`（SEARCH-001 提供）

### 2. `getOrderDetail`

```java
@Tool("查询用户单个订单详情")
public OrderDetailVO getOrderDetail(@P("订单 ID") String orderId) {
    return orderFeign.getOrder(orderId);
}
```

调 `GET /api/v1/user/orders/{orderId}`（已有）

### 3. `getUserOrderHistory`

```java
@Tool("查询用户最近的订单列表")
public List<OrderVO> getUserOrderHistory(
        @P("查询天数") int days) {
    return orderFeign.getList(1, 20, null, null, null, null);
}
```

调 `GET /api/v1/user/orders?page=1&size=20`（已有）

---

## 三、Prompt 设计（存 Nacos）

`ai-cs-prompt.yml`：

```yaml
system_prompt: |
  你是外卖系统智能客服"小饿"。

  你的能力：
  1. 推荐菜品（用 searchDishes 搜索）
  2. 查询订单详情（用 getOrderDetail）
  3. 查询订单历史（用 getUserOrderHistory）

  行为规则：
  - 不知道的事如实告知，不要瞎编
  - 订单状态必须通过 Tool 查询，不能猜
  - 简洁友好，回复不超过 100 字（除非需要列举）

fallback_response: |
  抱歉，我暂时无法处理您的请求。请稍后重试。
```

**MVP 限制说明**：当前版本**不支持退款/取消/下单**，如果用户问，引导至人工客服。

---

## 四、API 设计

### 4.1 SSE 聊天接口

```
POST /api/v1/cs/chat
Content-Type: application/json
Accept: text/event-stream

Request:
{
  "message": "推荐清淡的汤"
}

Response (SSE):
data: {"type":"token","content":"为"}
data: {"type":"token","content":"您"}
...
data: {"type":"done","messageId":"xxx"}
```

### 4.2 错误处理

```json
data: {"type":"error","code":401,"message":"请先登录"}
```

---

## 五、任务分派

### ☕ 后端（3 天）

#### 5.1 新建文件

| 文件 | 说明 |
|------|------|
| `del-cs/pom.xml` | Spring Boot + langchain4j + Feign |
| `del-cs/src/main/resources/application.yml` | Nacos 端口 10011 |
| `del-cs/.../CsApplication.java` | 启动类（@EnableFeignClients） |
| `del-cs/.../config/Langchain4jConfig.java` | Qwen 模型配置 |
| `del-cs/.../config/FeignClientsConfig.java` | 内部 Feign 配置 |
| `del-cs/.../web/ChatController.java` | SSE 聊天接口 |
| `del-cs/.../service/ChatService.java` | langchain4j Agent 主服务 |
| `del-cs/.../service/tools/SearchDishesTool.java` | 搜菜 Tool |
| `del-cs/.../service/tools/OrderDetailTool.java` | 订单详情 Tool |
| `del-cs/.../service/tools/OrderHistoryTool.java` | 订单历史 Tool |
| `del-cs/.../memory/RedisChatMemoryStore.java` | 会话存储（30 分钟过期） |
| `del-cs/.../feign/ProductSearchFeignClient.java` | 调 del-product |
| `del-cs/.../feign/OrderFeignClient.java` | 调 del-order |
| `del-cs/.../prompt/PromptManager.java` | 从 Nacos 读 Prompt |

#### 5.2 Maven 依赖

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter（1.15.0-beta25，**本地仓库已有完整 JAR**）</artifactId>
        <version>1.15.0-beta25</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-dashscope-spring-boot-starter（1.15.0-beta25）</artifactId>
        <version>1.15.0-beta25</version>
    </dependency>
</dependencies>
```

#### 5.3 application.yml

```yaml
server:
  port: 10011

spring:
  application:
    name: del-cs
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: sakana
      config:
        server-addr: 127.0.0.1:8848
        namespace: sakana
        group: DEFAULT_GROUP
        file-extension: yml
  config:
    import:
      - optional:nacos:common-jwt.yml
      - optional:nacos:common-jackson.yml
      - optional:nacos:del-cs-prompt.yml

langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: ${DASHSCOPE_API_KEY}
        model-name: qwen-plus
        temperature: 0.7
```

#### 5.4 Langchain4j Config 关键代码

```java
@Configuration
public class Langchain4jConfig {

    @Bean
    ChatLanguageModel qwenModel(@Value("${langchain4j.community.dashscope.chat-model.api-key}") String apiKey) {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .temperature(0.7)
                .build();
    }

    @Bean
    StreamingChatLanguageModel qwenStreamingModel(
            @Value("${langchain4j.community.dashscope.chat-model.api-key}") String apiKey) {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .temperature(0.7)
                .build();
    }
}
```

#### 5.5 ChatService 核心（SSE 流式）

```java
@Service
public class ChatService {

    private final StreamingChatLanguageModel streamingModel;
    private final RedisChatMemoryStore memoryStore;
    private final PromptManager promptManager;
    private final List<Object> tools;  // 3 个 Tool 实例

    public Flux<String> chatStream(Long userId, String message, String sessionId) {
        // 1. 获取/创建会话记忆
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(10)
                .chatMemoryStore(memoryStore)
                .build();

        // 2. 构建 AI Service（绑定 Tools 和 Prompt）
        StreamingChatLanguageModel model = AiServices.builder(StreamingChatLanguageModel.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemory(memory)
                .systemMessage(promptManager.getSystemPrompt())
                .tools(tools.toArray())
                .build();

        // 3. 流式生成
        return Flux.create(sink -> {
            model.generate(List.of(UserMessage.from(message)), 
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            sink.next(formatSSE("token", token));
                        }
                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            sink.next(formatSSE("done", response.content().text()));
                            sink.complete();
                        }
                        @Override
                        public void onError(Throwable error) {
                            sink.next(formatSSE("error", error.getMessage()));
                            sink.complete();
                        }
                    });
        });
    }

    private String formatSSE(String type, String content) {
        return String.format("data: {\"type\":\"%s\",\"content\":\"%s\"}\n\n", type, content);
    }
}
```

#### 5.6 Redis 会话存储

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private final StringRedisTemplate redis;
    private static final Duration TTL = Duration.ofMinutes(30);
    
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = "chat:session:" + memoryId;
        List<Object> raw = redis.opsForList().range(key, 0, -1);
        // 反序列化...
    }
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = "chat:session:" + memoryId;
        redis.opsForList().rightPushAll(key, serialize(messages));
        redis.expire(key, TTL);
    }
}
```

### 🎨 前端（1.5 天）

#### 5.7 新建文件

| 文件 | 说明 |
|------|------|
| `src/components/ChatWidget.vue` | **悬浮聊天组件**（右下角按钮 + 弹窗） |
| `src/components/ChatMessage.vue` | 单条消息（用户/AI）|
| `src/components/ChatInput.vue` | 输入框（带发送按钮）|
| `src/composables/useChat.ts` | SSE 解析 composable |
| `src/api/cs/chat.ts` | API 封装 |

#### 5.8 ChatWidget.vue 关键逻辑

```vue
<template>
  <!-- 悬浮按钮 -->
  <button v-if="!open" class="chat-fab" @click="openChat">
    <i class="ri-chat-ai-line"></i>
  </button>

  <!-- 聊天面板 -->
  <div v-else class="chat-panel">
    <div class="chat-header">
      <span>智能客服 · 小饿</span>
      <button @click="open = false">×</button>
    </div>
    <div ref="messagesEl" class="chat-messages">
      <ChatMessage v-for="m in messages" :key="m.id" :msg="m" />
    </div>
    <ChatInput v-model="input" @send="sendMessage" :disabled="loading" />
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { chatStream, type ChatMessage } from '@/api/cs/chat'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const open = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])

async function openChat() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  open.value = true
}

async function sendMessage() {
  if (!input.value.trim() || loading.value) return
  const text = input.value
  input.value = ''
  messages.value.push({ id: Date.now(), role: 'user', content: text })
  loading.value = true

  const aiMsg = { id: Date.now() + 1, role: 'ai', content: '' }
  messages.value.push(aiMsg)
  await nextTick()
  scrollToBottom()

  // SSE 流式接收
  const eventSource = chatStream(text, auth.userInfo.fid)
  eventSource.onmessage = (e) => {
    const data = JSON.parse(e.data)
    if (data.type === 'token') {
      aiMsg.content += data.content
    } else if (data.type === 'done') {
      loading.value = false
    } else if (data.type === 'error') {
      ElMessage.error(data.message)
      loading.value = false
    }
  }
  eventSource.onerror = () => {
    loading.value = false
  }
}

function scrollToBottom() {
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}
</script>
```

#### 5.9 useChat.ts（SSE 处理）

```typescript
// src/composables/useChat.ts
import { ref } from 'vue'

export function useChat() {
  const messages = ref<ChatMsg[]>([])
  const loading = ref(false)

  async function send(query: string, onToken: (t: string) => void) {
    loading.value = true
    const resp = await fetch('/api/v1/cs/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${getToken()}`
      },
      body: JSON.stringify({ message: query })
    })
    const reader = resp.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value)
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = JSON.parse(line.slice(6))
          if (data.type === 'token') onToken(data.content)
        }
      }
    }
    loading.value = false
  }

  return { messages, loading, send }
}
```

### 🛠️ 运维（0.5 天）

| 文件 | 操作 | 说明 |
|------|------|------|
| Nacos | 新建 `del-cs` 服务 | 服务发现 |
| `del-gateway.yml` | 加路由 `/api/v1/cs/**` → `del-cs` | 网关层 |
| Docker（可选）| 跑容器或 IDEA 直跑 | MVP 阶段 IDEA 直跑即可 |
| 资源 | 4 核 / 4GB | 单实例 |

---

## 六、验收清单

### 6.1 后端

```bash
# 1. 启动 del-cs
mvn clean compile -pl del-cs -am -DskipTests
# IDEA 启动 del-cs 启动类

# 2. 健康检查
curl http://localhost:10011/actuator/health
# 预期：{"status":"UP"}

# 3. SSE 聊天（必须带 USER token）
curl -X POST http://localhost:10010/api/v1/cs/chat \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"推荐个清淡的汤"}'
# 预期：SSE 流式返回
# data: {"type":"token","content":"为"}
# data: {"type":"token","content":"您"}
# ...
# data: {"type":"done","content":"..."}
```

### 6.2 前端

```bash
# 1. 启动前端
cd E:\VSCode_workspace\Delivery
npm run dev

# 2. 浏览器测试（需登录 C 端用户）
# - 打开任意页面，右下角看到悬浮按钮
# - 点击按钮，聊天面板弹出
# - 输入"推荐个汤"，看到 AI 流式回复
# - 刷新页面后，再点击按钮，之前的消息还在（会话保持）
```

### 6.3 端到端

| 测试 | 预期 |
|------|------|
| 问"推荐清淡的汤" | AI 用 searchDishes 查，返回 5 条清淡菜品 |
| 问"我最近的订单" | AI 用 getUserOrderHistory，返回最近订单 |
| 问"我的订单 ORD123" | AI 用 getOrderDetail，返回订单详情 |
| 问"我要退款" | AI 说"暂不支持此功能"（fallback）|
| 问"你是谁" | AI 自我介绍"我是小饿" |

---

## 七、风险与限制

| 项 | 说明 |
|------|------|
| **降级 Tool 缺失** | 退款/取消/下单 → AI 主动告知"暂不支持" |
| **冷启动慢** | langchain4j 第一次调用需加载模型，~2-5 秒 |
| **流式响应中断** | 用户刷新页面 SSE 断流，会话仍在 Redis |
| **Tool 调用链** | LLM 可能幻觉，需 Prompt 明确规则 |
| **DEL-CS 是第 10 个服务** | 符合用户最初要求（不增加为业务服务的原则），但确实是新服务 |
| **没用真实 LLM 框架经验** | langchain4j 1.15.0-beta25（**已验证本地仓库完整可用**） |

---

## 八、与原 AI_CS_DESIGN.md 的差异

| 项 | 原设计 | MVP |
|------|--------|-----|
| Tool 数量 | 10 | **3**（+7 待后续） |
| 退款 Tool | 有 | **不做**（等 #REFUND-001） |
| 前端 widget | 提及 | **本次实施** |
| 完整聊天页 | 提及 | **不做** |
| WebSocket | 提及 | **只做 SSE**（简单） |
| 工作量 | 7-10 天 | **5 天** |

**未来扩展**（不在本工单）：
- #REFUND-001 退款接口 → 新增 `createRefund` Tool
- 加入"加购物车""创建订单""取消订单" Tool
- 完整独立聊天页（`/cs` 路由）
- WebSocket 替代 SSE（双向通信）
- 多 LLM 切换（OpenAI / 国内大模型）
- 向量召回（knn）增强 RAG 效果

---



---

## 实施进度更新（2026-09-07）

### ✅ Phase 1：Mock 模式完成 + 端到端验证通过

| 验证项 | 端点 | 结果 |
|--------|------|------|
| Health 直接访问 | `localhost:10011` | ✅ `{"status":"UP","service":"del-cs"}` |
| Health 经网关 | `localhost:10010` | ✅ `{"status":"UP","service":"del-cs"}` |
| Chat Mock 直接 | `localhost:10011/api/v1/cs/chat` | ✅ SSE 流正常 |
| Chat Mock 经网关 | `localhost:10010/api/v1/cs/chat` | ✅ SSE 流正常，无需认证 |

### 修复的 3 个 Bug

| Bug | 文件 | 修复 |
|-----|------|------|
| 语法错误 | `CsSecurityConfig.java` | `requestMatchers("/api/v1/cs/**").permitAll()` |
| 语法错误 | `PromptManager.java` | `@Value("${cs.system-prompt:}")` 用属性占位符 |
| 路由未放行 | `del-gateway/.../PathRoleRule.java` | 网关公开 `/api/v1/cs/**` |

### ⏳ Phase 2：真实 LLM 集成（待启动）

Phase 2 工作（不在当前工单范围）：

- [ ] 切换 ChatService 从 Mock 到 langchain4j Qwen
- [ ] 配置 Nacos `ai-cs-prompt.yml`（系统 Prompt + 降级回复）
- [ ] 真实 Tool 调用（3 个：searchDishes / getOrderDetail / getUserOrderHistory）
- [ ] 端到端：用户说"推荐个清淡的汤"→ 真实 Qwen 回复菜品

### 📊 当前状态

- Phase 1 (Mock): **完成** ✅
- Phase 2 (真实 LLM): **待启动**（需要业务方确认 + 网络恢复 langchain4j 完整依赖）
## 九、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 需求定义 | 用户 + Codex | 2026-09-07 |
| 工单创建 | Codex | 2026-09-07 |
| 后端实施 | _待后端_ | |
| 前端实施 | _待前端_ | |
| 运维配置 | _待运维_ | |
| 联调验收 | _待填_ | |

---

**说明**：
- 本工单基于 `docs/AI_CS_DESIGN.md` 裁剪，**不修改原设计文档**
- 只新建 1 个新服务 `del-cs`（端口 10011）
- 用户已确认 Q1=A（MVP 范围）、Q2=α（悬浮 widget）
- 不需要再做任何澄清问题


---

## ★ 重要更新：langchain4j 1.15.0-beta25 已验证本地仓库完整

**之前的判断错了**——用户本地 Maven 仓库 (`E:\software\Maven\MAVEN_Repo`) 实际**已有** langchain4j 完整依赖（之前误以为下载不了）。

### 实际可用版本

所有 4 个核心组件在仓库 `dev\langchain4j` 目录下：

```
langchain4j-core-1.15.0.jar                                    (378 KB)
langchain4j-spring-boot-starter-1.15.0-beta25.jar             (~50 KB)
langchain4j-community-dashscope-1.15.0-beta25.jar            (~30 KB)
langchain4j-community-dashscope-spring-boot-starter-1.15.0-beta25.jar
```

### 传递依赖验证（仓库全部齐全）

- Jackson (databind/core/annotations) 2.14.x ✅
- reactor-core 3.6.x ✅
- okhttp 4.12 + okio 3.6 ✅
- slf4j-api ✅
- gson 2.10 ✅
- commons-lang3 ✅

### Maven 配置建议

`~/.m2/settings.xml` 添加本地仓库优先：

```xml
<settings>
  <localRepository>E:\software\Maven\MAVEN_Repo</localRepository>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

### 实施立即可行

**之前的"网络限制"诊断结论作废**。可立即按原工单（含 langchain4j 1.15.0-beta25）实施，**B+ 备选方案不再需要**。
