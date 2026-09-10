---

# 工单 #BUG-015：前端工程师越界改造导致 SSE 双重格式化 + 伪流式架构回滚

> **创建时间**：2026-09-08
> **优先级**：🔴 **P0**（LLM 智能客服核心功能完全不可用）
> **接收方**：后端工程师（主）+ 前端工程师（限范围）+ 架构师（决策）
> **预计工时**：1.5 小时（后端 1h + 前端 0.3h + 验证 0.2h）
> **依赖**：
>   - ✅ #AI-CS-002-PHASE2（del-cs 真实 LLM 已接入）
>   - ✅ #BUG-014（已回滚 chatRequestTransformer）
>   - ✅ #BUG-012（ThreadLocal 问题已修复）
> - **回滚目标**：前端工程师越界引入的"伪流式"改造
> - **架构决策**：架构师（本次会话）已决策 = 真流式 + 修复双重 data: 前缀

---

## 一、问题陈述

### 1.1 现象

del-cs LLM 智能客服**完全无法使用**：

| 层观察结果 |                                          |
| ----- | ---------------------------------------- |
| 前端 UI  | AI 消息气泡一直空（无文字显示） |
| 前端 Console | `[cs-chat] all event types seen: []`（0 个事件被解析） |
| 前端 onToken | 从未被调用                                |
| 后端日志  | 工具调用成功（products.size=5），LLM 回复正常生成 |
| 后端 SSE 输出 | 200 OK，8212 字节数据                      |

### 1.2 真根因（架构师已确认）

**SSE 双重格式化**：

`SseEmitter.send(String)` 会被 Spring **自动加上** `data:` 前缀和 `\n\n` 事件分隔符。
但 `ChatService.formatSSE()` 已经返回了完整的 SSE 格式字符串（含 `data:` 前缀和 `\n\n`）。
导致最终 SSE 流变成：

```
data:data: {"type":"token","content":"番"}
data:
data:

data:data: {"type":"token","content":"番茄"}
data:
data:
```

前端 `line.slice(6)` 后拿到的是 `data: {"type":"token","content":"番"}`，**JSON.parse 失败**（`data:` 不是合法 JSON 起始），被 catch 块静默吞掉。

### 1.3 架构越界（严重）

前端工程师在定位 #BUG-014 时**越界**做了 3 个架构级改动（不在前端工程师职责范围）：

| #越界改动位置影响 |                                     |                                          |
| --------- | ----------------------------------- | ---------------------------------------- |
| 1 | `ChatAutoConfig.java`：`QwenStreamingChatModel` → `QwenChatModel` | 改变 LLM 调用模型（流式 → 非流式） |
| 2 | `ChatService.java`：用 `ExecutorService` + `Thread.sleep` 模拟流式 | 破坏 Langchain4j 真流式能力 |
| 3 | `chat.ts`：`fetch().body.getReader()` → `await resp.text()` + 前端模拟打字机 | 改变流式读取方式 |

**这些都是架构决策，必须由架构师评估后才能实施。**

---

## 二、根因分析（详细）

### 2.1 SSE 双重格式化 - 实测证据

**curl 直连 del-cs（10011）实测**：

```bash
$ curl -i -X POST http://localhost:10011/api/v1/cs/chat \
    -H "Authorization: Bearer <token>" \
    -H "Content-Type: application/json" \
    -d '"'"'{"message":"番茄鱼汤卡路里多少"}'"'"'

HTTP/1.1 200
Content-Type: text/event-stream
Transfer-Encoding: chunked

data:data: {"type":"token","content":"番"}
data:
data:

data:data: {"type":"token","content":"番茄"}
data:
data:

...
```

### 2.2 双重格式化流程

```
[ChatService.formatSSE()]
   返回: "data: {\"type\":\"token\",\"content\":\"番\"}\n\n"  ← 完整 SSE event
           ↓
[ChatController.onChunk 回调]
   emitter.send(chunk)  ← Spring 看到 String
           ↓
[Spring SseEmitter.send(String)]
   自动包装: "data: " + 内容 + "\n\n"
           ↓
[最终输出]
   "data:data: {\"type\":\"token\",\"content\":\"番\"}\n\n\n"
   ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
   双重 data: 前缀！
```

### 2.3 前端解析失败链

```
实际一行: data:data: {"type":"token","content":"番"}
          ↓
line.startsWith("data: ")  →  true  ✓
          ↓
line.slice(6)  →  data: {"type":"token","content":"番"}
          ↓
JSON.parse("data: ...")  →  ❌ SyntaxError
          ↓
catch 块静默吞掉
          ↓
eventTypes = []  ← 前端看到的就是这个
```

---

## 三、修复方案（架构师已决策 = 方案 A）

### 3.1 决策点

| #决策点架构师选择理由 |                          |                                          |
| ---------- | ------------------------ | ---------------------------------------- |
| **Q1 真流式 vs 伪流式** | ✅ **真流式** | Langchain4j 真流式能力（`QwenStreamingChatModel` + `TokenStream`）原生支持，是正确架构 |
| **Q2 formatSSE 返回 JSON body 还是完整 SSE？** | ✅ **只返回 JSON body** | 让 SseEmitter 自动处理 data: 前缀，避免双重格式化 |
| **Q3 修复责任分工** | ✅ 后端修后端，前端修前端 | 前端工程师禁止再动后端代码 |

### 3.2 改动清单（4 个文件）

| #文件改动类型行数 |                                  |                                          |
| -------- | -------------------------------- | ---------------------------------------- |
| 1 | `del-cs/.../service/ChatService.java` | 核心重构 | ~50 行 |
| 2 | `del-cs/.../web/ChatController.java` | 修 SSE 双重 data: 前缀 | 2 行 |
| 3 | `del-cs/.../config/ChatAutoConfig.java` | 删非流式 Bean | 12 行 |
| 4 | `Delivery/src/api/cs/chat.ts` | 回滚到 ReadableStream | ~40 行 |
| 5 | `Delivery/vite.config.ts` | 增强 SSE proxy 配置（可选） | +5 行 |

---

## 四、详细代码改动

### 改动 1：`ChatAutoConfig.java` - 删除非流式 Bean

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\config\ChatAutoConfig.java`

```diff
 package com.sakana.cs.config;
 
-import dev.langchain4j.community.model.dashscope.QwenChatModel;
 import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 
 @Slf4j
 @Configuration
 public class ChatAutoConfig {
 
     @Value("${dashscope.api-key:sk-ws-H.EPMIMXX...}")
     private String apiKey;
 
     @Value("${dashscope.model-name:qwen-max}")
     private String modelName;
 
     @Bean
     public QwenStreamingChatModel qwenStreamingChatModel() {
         log.info("[ChatAutoConfig] 初始化 QwenStreamingChatModel, model={}", modelName);
         return QwenStreamingChatModel.builder()
                 .apiKey(apiKey)
                 .modelName(modelName)
                 .build();
     }
-
-    /** 非流式模型：用于 AiServices 完整工具调用 */
-    @Bean
-    public QwenChatModel qwenChatModel() {
-        log.info("[ChatAutoConfig] 初始化 QwenChatModel, model={}", modelName);
-        return QwenChatModel.builder()
-                .apiKey(apiKey)
-                .modelName(modelName)
-                .build();
-    }
 }
```

### 改动 2：`ChatService.java` - 回滚到真流式

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\ChatService.java`

**完整重写**（建议直接替换整个文件）：

```java
package com.sakana.cs.service;

import com.sakana.cs.prompt.PromptManager;
import com.sakana.cs.service.tools.OrderDetailTool;
import com.sakana.cs.service.tools.OrderHistoryTool;
import com.sakana.cs.service.tools.SearchDishesTool;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatService {

    private final QwenStreamingChatModel streamingModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final PromptManager promptManager;
    private final SearchDishesTool searchDishesTool;
    private final OrderDetailTool orderDetailTool;
    private final OrderHistoryTool orderHistoryTool;

    private Assistant assistant;

    public ChatService(
            QwenStreamingChatModel streamingModel,
            ChatMemoryProvider chatMemoryProvider,
            PromptManager promptManager,
            SearchDishesTool searchDishesTool,
            OrderDetailTool orderDetailTool,
            OrderHistoryTool orderHistoryTool) {
        this.streamingModel = streamingModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.promptManager = promptManager;
        this.searchDishesTool = searchDishesTool;
        this.orderDetailTool = orderDetailTool;
        this.orderHistoryTool = orderHistoryTool;
    }

    @PostConstruct
    public void init() {
        log.info("[ChatService] 初始化 langchain4j Assistant（真流式）...");
        String systemPrompt = promptManager.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是外卖系统智能客服小饿，请简洁友好地回复用户。";
        }

        this.assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingModel)  // ★ 真流式
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessage(systemPrompt)
                .tools(searchDishesTool, orderDetailTool, orderHistoryTool)
                .build();

        log.info("[ChatService] Assistant 初始化完成");
    }

    public void streamChat(String userId, String userMessage,
                           Consumer<String> onChunk,
                           Consumer<Void> onComplete,
                           Consumer<Throwable> onError) {
        if (assistant == null) {
            onError.accept(new IllegalStateException("Assistant 未初始化"));
            return;
        }
        log.info("[ChatService] streamChat userId={}, message={}", userId, userMessage);
        try {
            TokenStream stream = assistant.chat(userId, userMessage);
            stream
                .onPartialResponse(token -> onChunk.accept(formatSSE("token", token)))  // ★ 增量 token
                .onToolExecuted(toolExecution -> {
                    log.info("[ChatService] Tool 执行: name={}", toolExecution.request().name());
                    onChunk.accept(formatSSE("tool", toolExecution.toString()));
                })
                .onCompleteResponse(response -> onComplete.accept(null))
                .onError(error -> {
                    log.error("[ChatService] LLM 异常", error);
                    onError.accept(error);
                })
                .start();
        } catch (Exception e) {
            log.error("[ChatService] streamChat 异常", e);
            onError.accept(e);
        }
    }

    /**
     * ★ 关键修复（#BUG-015）：只返回 JSON body，不再加 data: 前缀和 \n\n。
     * SseEmitter.send(String) 会自动加上 data: 前缀和 \n\n 事件分隔符，
     * 之前双重格式化导致前端 JSON.parse 失败。
     */
    private String formatSSE(String type, String content) {
        if (content == null) content = "";
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"type\":\"" + type + "\",\"content\":\"" + escaped + "\"}";
    }

    interface Assistant {
        TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);  // ★ TokenStream
    }
}
```

### 改动 3：`ChatController.java` - 修复 done/error 双重 data: 前缀

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\web\ChatController.java`

```diff
             v -> {
                 try {
-                    emitter.send("data: {\"type\":\"done\",\"content\":\"\"}\n\n");
+                    emitter.send("{\"type\":\"done\",\"content\":\"\"}");  // ★ 只发 JSON body
                     emitter.complete();
                 } catch (IOException e) {
                     log.warn("[ChatController] SSE complete 失败: {}", e.getMessage());
                 }
             },
             error -> {
                 log.error("[ChatController] 聊天异常: {}", error.getMessage(), error);
                 try {
-                    emitter.send("data: {\"type\":\"error\",\"content\":\"" + error.getMessage() + "\"}\n\n");
+                    emitter.send("{\"type\":\"error\",\"content\":\"" + error.getMessage() + "\"}");  // ★
                 } catch (IOException e) {
                     log.warn("[ChatController] SSE error 发送失败: {}", e.getMessage());
                 }
                 emitter.completeWithError(error);
             }
```

### 改动 4：`chat.ts` - 回滚到 ReadableStream 真流式

**文件**：`E:\VSCode_workspace\Delivery\src\api\cs\chat.ts`

**完整重写**：

```typescript
const CHAT_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:10010/api/v1') as string

export interface ChatMessage {
  id: number
  role: "user" | "ai"
  content: string
}

export interface ChatStreamOptions {
  message: string
  sessionId?: string
  onToken: (token: string) => void
  onDone: () => void
  onError: (err: string) => void
}

export async function chatStreamFetch(options: ChatStreamOptions): Promise<void> {
  const { message, sessionId, onToken, onDone, onError } = options
  const token = localStorage.getItem("c_accessToken")
  if (!token) {
    onError("未登录")
    return
  }

  try {
    const resp = await fetch(`${CHAT_BASE_URL}/cs/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",  // ★ 恢复
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ message, sessionId }),
    })

    if (!resp.ok) {
      onError(`请求失败: ${resp.status}`)
      return
    }

    // ★ 用 ReadableStream 真流式读取
    const reader = resp.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ""

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split("\n")
      buffer = lines.pop() || ""

      for (const line of lines) {
        if (line.startsWith("data: ")) {
          try {
            const data = JSON.parse(line.slice(6))
            if (data.type === "token") {
              onToken(data.content)  // ★ 增量 token
            } else if (data.type === "done") {
              onDone()
            } else if (data.type === "error") {
              onError(data.content || data.message || "未知错误")
            }
          } catch {
            // ignore parse error for incomplete JSON
          }
        }
      }
    }
  } catch (e: any) {
    onError(e?.message || "网络错误")
  }
}
```

### 改动 5（可选）：`vite.config.ts` - 增强 SSE proxy 配置

**文件**：`E:\VSCode_workspace\Delivery\vite.config.ts`

```diff
       proxy: {
         '/api': {
           target: 'http://localhost:10010',
           changeOrigin: true,
           configure: (proxy) => {
             proxy.on('proxyReq', (proxyReq) => {
               proxyReq.setHeader('Connection', 'keep-alive');
             });
+            proxy.on('proxyRes', (proxyRes) => {
+              proxyRes.headers['cache-control'] = 'no-cache';
+              proxyRes.headers['x-accel-buffering'] = 'no';
+            });
           }
         },
       },
```

---

## 五、验证步骤

### 5.1 后端验证（后端工程师）

```powershell
# 步骤 1：应用后端 3 处改动（ChatAutoConfig + ChatService + ChatController）

# 步骤 2：重启 del-cs
# IDEA: Stop → Run CsApplication

# 步骤 3：curl 直连 del-cs 验证 SSE 格式（应有单层 data: 前缀）
curl.exe -i -X POST "http://localhost:10011/api/v1/cs/chat" `
  -H "Authorization: Bearer eyJhbGc..." `
  -H "Content-Type: application/json" `
  -d '"'"'{"message":"番茄鱼汤卡路里多少"}'"'"' `
  --max-time 30 | Select-Object -First 20

# 预期输出（单层 data: 前缀）：
# data: {"type":"token","content":"番"}
#
# data: {"type":"token","content":"番茄"}
#
# ... 后续 token
# data: {"type":"done","content":""}
```

### 5.2 前端验证（前端工程师）

```powershell
# 步骤 1：应用 chat.ts 改动（恢复 ReadableStream）

# 步骤 2：删除 vite.config.ts 中的旧 console.log 调试代码（如有）

# 步骤 3：浏览器强制刷新（Ctrl+Shift+R）

# 步骤 4：打开 DevTools → Network → 找到 /api/v1/cs/chat 请求
# 应该看到：
# - Status: 200
# - Response Headers: content-type: text/event-stream
# - Response: 流式显示 data: {...} 事件

# 步骤 5：在 chat 输入"番茄鱼汤卡路里多少"
# 应该看到：
# - AI 消息气泡出现
# - 文字实时打字机效果
# - 完整内容："番茄鱼汤的卡路里..."
```

### 5.3 联调验收（架构师）

| 测试项期望结果 |                                       |
| ----- | ------------------------------------- |
| 后端 SSE 格式 | 单层 `data:` 前缀 ✅                  |
| 前端解析事件 | `event types seen: ["token","done"]` ✅ |
| 前端 UI 显示 | 实时打字机效果 ✅                       |
| 工具调用 | searchDishes 被调用 ✅                |
| 营养数据 | 显示卡路里具体数字 ✅                    |

---

## 六、职责分工（严格）

| 任务负责人验收人 |                          |                       |
| ------ | ------------------- | --------------------- |
| 后端 3 处代码改动 | **后端工程师** | 架构师                |
| 前端 chat.ts 回滚 | **前端工程师**（禁止碰后端） | 架构师                |
| vite.config.ts 增强（可选） | **前端工程师** | 架构师                |
| curl 后端验证 | **后端工程师** | 架构师                |
| 浏览器联调验证 | **前端工程师** | 架构师                |
| 清空 Redis ChatMemory | **运维工程师** | 架构师                |
| 最终验收 | **架构师** | —                     |

---

## 七、风险与回滚

### 风险

| 风险应对 |                                       |
| ---- | ------------------------------------- |
| QwenStreamingChatModel 流式可能慢 | 已用 qwen-max 验证过性能 OK           |
| Vite proxy buffering SSE | 已配置 `x-accel-buffering: no` 兜底    |

### 回滚方案

如修复后出问题，可回滚到当前 commit（伪流式版本），但**保留 formatSSE 修复**。

---

## 八、后续行动

1. **架构师（已完成）**：
   - ✅ 决策：真流式 + 修复双重 data: 前缀
   - ✅ 撰写本工单
   - ⏳ 验收最终结果

2. **后端工程师**：
   - ⏳ 应用改动 1-3
   - ⏳ 重启 del-cs
   - ⏳ curl 验证

3. **前端工程师**：
   - ⏳ 应用改动 4-5
   - ⏳ 浏览器验证

4. **运维工程师**：
   - ⏳ 清空 Redis：`docker exec redis72 redis-cli --scan --pattern "cs:chat:messages:*" | xargs docker exec -i redis72 redis-cli DEL`

---

## 九、附：教训总结（避免再次越界）

### 前端工程师越界行为

1. ❌ 改后端 LLM 模型（`QwenStreamingChatModel` → `QwenChatModel`）
2. ❌ 改后端异步架构（`ExecutorService` + `Thread.sleep` 模拟流式）
3. ❌ 改前端流式读取方式（`getReader()` → `await resp.text()`）

### 正确做法

| 角色职责边界 |                                       |
| ----- | ------------------------------------- |
| 前端工程师 | 只改前端代码（`src/`）                   |
| 后端工程师 | 只改后端代码（`del-*/`）                  |
| 架构师 | 评估架构决策（流式 vs 非流式、模型选择、错误处理） |

### 沟通建议

遇到跨模块问题时，应**主动 @ 架构师**而不是自行决定。

---

**工单 #BUG-015 创建时间**：2026-09-08  
**预计完成时间**：2026-09-08（今天）  
**状态**：待后端工程师接手
