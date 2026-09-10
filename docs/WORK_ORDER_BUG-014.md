

---

# 工单 #BUG-014：强制 Function Calling 解决 qwen-plus 自信问题

> **创建时间**：2026-09-08
> **优先级**：🔴 **P0**（LLM 客服核心能力失效）
> **接收方**：后端
> **预计工时**：1-2 小时（含调研 + 验证）
> **依赖**：
>   - ✅ #BUG-013（Tool 描述 + Prompt 修过了，但模型仍可能忽略）
>   - ✅ #AI-CS-002-PHASE2（langchain4j 已集成）
>   - 当前 langchain4j 版本：**1.15.0-beta25**

---

## 一、问题陈述

### 1.1 现象

即使修复了 Tool 描述（#BUG-013）和 Prompt，**qwen-plus 模型仍可能在某些场景下不调用工具**：

- 模型训练数据够丰富 → 觉得"我知道" → 跳过工具调用
- 用户问"清淡的菜有什么" → 模型直接编答案（基于训练知识）
- 模型自信程度越高 → 工具调用率越低

### 1.2 用户决策（2026-09-08）

**采用方案 B：强制 Function Calling**

> **qwen-plus 过于自信，凭训练知识回答而不调工具。强制 Function Calling 从机制上杜绝此问题。**

### 1.3 方案对比

| 维度 | 方案 A：强化 Prompt | **方案 B：强制 Function Calling** ✅ |
|------|-----------|-----------|
| 强制力 | ⚠️ 模型可选忽略 | ✅ **模型必须调用，无法跳过** |
| 实现复杂度 | ✅ 5 分钟（改 Nacos） | ⚠️ 1-2 小时（含调研）|
| 效果稳定性 | ⚠️ 因模型而异 | ✅ 稳定可靠 |
| 对 qwen-plus | 一般 | ✅ **好** |

---

## 二、根因分析

LLM 调用工具的行为是**概率性的**：
- Prompt 说"应该调工具"→ LLM 看情况决定（10-30% 概率会跳过）
- 系统级"强制"机制（tool_choice=any / strict mode）→ LLM 必须调（100%）

qwen-plus 是 DashScope 的中等模型，在"看起来我懂"的场景下倾向自信回答。要根治必须在**模型调用层**强制，不是 Prompt 层。

---

## 三、修复方案（方案 B）

### 3.1 实施思路

通过 langchain4j 配置 ChatModel 的"工具调用模式"为"强制"。

### 3.2 关键技术点

**需要调研的具体 API**（langchain4j 1.15.0-beta25）：

| API | 说明 | 在哪 |
|-----|------|------|
| `QwenChatModel.builder()` | DashScope 模型构造器 | langchain4j-community-dashscope |
| `strictTools(boolean)` | 是否严格模式 | 可能不存在或改名 |
| `toolExecutionMode(...)` | AiServices 上的工具执行模式 | langchain4j-core |
| `ParallelToolExecutionMode.STRICT` | 严格并行执行 | 可能 |

**⚠️ 注意**：langchain4j 1.15 的 API 我不能 100% 确认，需要后端工程师查官方文档或源码验证。

### 3.3 改动 1（主要）：强制 Tool 调用

**文件**：`del-cs/src/main/java/com/sakana/cs/config/ChatAutoConfig.java`

```java
@Bean
public StreamingChatLanguageModel qwenStreamingModel() {
    return QwenChatModel.builder()
            .apiKey(apiKey)
            .modelName("qwen-plus")
            .temperature(0.7)
            // ★ 强制工具调用（具体 API 待查文档后确认）
            .strictTools(true)    // 方法可能不存在，需替换
            .build();
}
```

**备选 API（如果 `strictTools` 不存在）**：

```java
// 备选 1：覆盖 ChatRequest 的 tool_choice 参数
.chatRequestTransformer(req -> {
    req.setToolChoice("any");  // OpenAI 标准参数
    return req;
})

// 备选 2：用 AiServices 的 toolExecutionMode
AiServices.builder(Assistant.class)
    .streamingChatLanguageModel(...)
    .chatMemoryProvider(...)
    .systemMessageProvider(...)
    .toolExecutionMode(ToolExecutionMode.CRITICAL)  // 不存在的话换 ANY
    .tools(...)
    .build();
```

**Action Item**：后端工程师**必须先查 langchain4j 1.15.0-beta25 的官方文档**确认准确 API 名称。

### 3.4 改动 2：ChatService 配置 Tool Execution Mode

**文件**：`del-cs/src/main/java/com/sakana/cs/service/ChatService.java`

```java
@PostConstruct
public void init() {
    log.info("[ChatService] 初始化 langchain4j Assistant（强制工具调用）...");
    
    this.assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(streamingModel)
            .chatMemoryProvider(chatMemoryProvider)
            .systemMessage(promptManager.getSystemPrompt())
            .tools(searchDishesTool, orderDetailTool, orderHistoryTool)
            // ★ 强制模式：模型不能跳过工具调用
            // (具体方法名待确认)
            .strictTools(true)
            .build();
    
    log.info("[ChatService] Assistant 初始化完成（强制工具模式）");
}
```

### 3.5 改动 3（备选）：改 Qwen 模型参数

如果 langchain4j 没暴露强制调用的 API，可以**直接调 DashScope HTTP API**（绕过 langchain4j 的 chatModel）：

```java
// 在 ChatService.streamChat 里直接调 DashScope HTTP API
// 而不是用 AiServices.builder().build()
@PostConstruct
public void init() {
    // 不再使用 langchain4j 的 AiServices
    // 直接用 RestTemplate 调 DashScope API
}
```

**不推荐**：增加工作量，但作为兜底。

---

## 四、调研任务（最重要）

### 4.1 第一步：查 langchain4j 1.15.0 API

**仓库**：`E:\software\Maven\MAVEN_Repo\dev\langchain4j`

```bash
# 反编译 chat-language-model 相关 JAR
# 或者直接看 API 文档：
# https://docs.langchain4j.dev/apidocs/dev/langchain4j/service/AiServices.html
```

**要找的方法**：
- 类 `AiServices` 上的 builder 方法
- 类 `ChatModel` / `StreamingChatLanguageModel` 上的配置项
- 是否有 `strictTools()`, `forceTools()`, `requireToolCall()` 之类的方法

### 4.2 第二步：看 QwenChatModel 源码

**路径**：`E:\software\Maven\MAVEN_Repo\dev\langchain4j\langchain4j-community-dashscope\1.15.0-beta25\langchain4j-community-dashscope-1.15.0-beta25-sources.jar`

```bash
# 解压找 QwenChatModel.java
# 看有没有 strictTools 或类似字段
```

### 4.3 第三步：判断可选方案

| 情况 | 行动 |
|------|------|
| 找到 `strictTools(true)` 类似方法 | 直接用 |
| 找到 `toolExecutionMode(...)` | 用这个 |
| 都没有 | 备选 1（ChatRequest 转换）或备选 2（直接调 HTTP） |

---

## 五、验证清单

### 5.1 后端编译

```bash
mvn clean compile -pl del-cs -am -DskipTests
```

预期：BUILD SUCCESS

### 5.2 重启 del-cs

### 5.3 端到端测试

```bash
USER_TOKEN="<USER 的 JWT>"

# 各种可能不调工具的提问
for q in "番茄鱼汤卡路里多少" "推荐几个清淡的菜" "有什么牛肉菜" "我经常点的菜有哪些"; do
    echo "=== Q: $q ==="
    curl -X POST "http://localhost:10010/api/v1/cs/chat" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"message\":\"$q\"}" | head -50
    echo ""
done
```

**关键验证**：每个响应里**必须**有 `data: {"type":"tool"...}` 事件（证明工具被调用）

### 5.4 curl 单次验证

```bash
USER_TOKEN="<USER 的 JWT>"

curl -X POST "http://localhost:10010/api/v1/cs/chat" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"番茄鱼汤卡路里多少"}'
```

预期 SSE 输出：
- ✅ 含 `tool` 事件
- ✅ 最终回复包含具体数字（"约 150 千卡"），不是"暂无数据"

---

## 六、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| langchain4j 1.15 没有强制工具 API | 🟡 P2 | 用备选方案 |
| 强制模式导致 LLM 死循环（试图调不存在的工具）| 🟢 P3 | 限制最大步数 |
| 强制模式对 qwen-max 也生效（虽然现在用 qwen-plus）| 🟢 P3 | 同代码不需改 |

---

## 七、关联工单

| 工单 | 状态 | 备注 |
|------|------|------|
| #BUG-013 | ✅ Tool 描述 + Prompt 改过 | 但还不足够 |
| **#BUG-014（本工单）**| 🔴 系统级强制 | 根治问题 |

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 方案决策 | 用户 | 2026-09-08 ✅ |
| 调研 langchain4j API | _待后端_ | 30 分钟 |
| 代码改动 | _待后端_ | 30-60 分钟 |
| 端到端验证 | _待_ | 10 分钟 |

---

## 九、给后端工程师的研究指南

**最重要的第一步**：先查 langchain4j 1.15.0-beta25 的源码

```powershell
# 1. 看 AiServices 类的方法
# 仓库路径: E:\software\Maven\MAVEN_Repo\dev\langchain4j\langchain4j-core\1.15.0\*.jar
# 用 jar 命令查看（如果有 Java 反编译工具）
# 或者直接看 docs.langchain4j.dev

# 2. 看 QwenChatModel 类
# E:\software\Maven\MAVEN_Repo\dev\langchain4j\langchain4j-community-dashscope\1.15.0-beta25\langchain4j-community-dashscope-1.15.0-beta25-sources.jar
# 找 strictTools, forceTools, requiredTools 之类的方法

# 3. 看 ChatModel 接口
# 是否有 setStrictTools / setForceToolCalls 之类的方法
```

**如果找不到严格模式 API**：用备选 1（ChatRequest 转换）

```java
StreamingChatLanguageModel model = QwenChatModel.builder()
    .apiKey(apiKey)
    .modelName("qwen-plus")
    .temperature(0.7)
    .build();

// 用代理强制 tool_choice=any
return new StreamingChatLanguageModel() {
    @Override
    public void chat(...) {
        // 拦截请求，强制加 tool_choice
    }
};
```

---

## 十、给运维工程师的反馈

**你的方案对比分析非常专业**。采纳方案 B 是正确的——Prompt 层是软约束，模型层是硬约束。LLM 应用工程的通用原则：**重要的逻辑必须在最底层强制**。

预计 1-2 小时可完成（含 API 调研时间）。

修完后再做端到端验证（用 #BUG-013 同样的方法：看完整 SSE 输出确认 tool 事件）。
