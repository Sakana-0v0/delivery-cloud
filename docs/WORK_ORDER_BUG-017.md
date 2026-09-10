---

# 工单 #BUG-017：qwen-max 简洁问题跳过工具 + Prompt 强化

> **创建时间**：2026-09-08
> **优先级**：🟡 **P2**（功能可用但数据不可靠）
> **接收方**：后端 + 架构师（决策 Prompt）
> **预计工时**：30 分钟
> **依赖**：
>   - ✅ #BUG-015（SSE 双重 data: 前缀已修复）
>   - ✅ #BUG-016（前端 SSE 行格式匹配已修复）
> - **关联**：#BUG-018（订单查询类型不匹配，需同步更新 Prompt）

---

## 一、问题陈述

### 1.1 现象

测试 "番茄鱼汤卡路里多少"：
- ❌ qwen-max **跳过 searchDishes 工具**，直接编答案"150卡路里"
- ❌ 还补充说"这个数值是我们系统中的一般估计"——**这是编造，不是真实数据**

测试 "请帮我搜索番茄鱼汤并告诉我卡路里"：
- ✅ 调用了 searchDishes 工具，返回真实数据

### 1.2 影响

| 问题业务影响 |                                       |
| ----- | ------------------------------------- |
| 数据准确性 | 🔴 高 — 用户可能拿到错误信息（编造数据） |
| 用户信任 | 🔴 高 — 用户不知道数据是编的 |
| 功能可用性 | 🟢 暂无 — 功能仍能用，只是数据不可靠 |

---

## 二、根因分析

### 2.1 qwen-max 模型特性

- qwen-max 是 DashScope **最贵、最强**的模型
- 训练知识丰富，在"看起来简单"的问题上倾向**自信地直接回答**
- Prompt 强化对 qwen-max **效果有限**（之前 #BUG-013 已经验证过）

### 2.2 Prompt 已强化但仍不够

当前 Nacos Prompt 已经有"必须调工具"规则，但 qwen-max 在简单问题上仍然跳过。

### 2.3 Temperature 默认值过高

`ChatAutoConfig` 用默认值（未显式设置）：
```java
return QwenStreamingChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        // 没有 .temperature().topP()
        .build();
```

DashScope 默认 temperature=1.0、top_p=0.8 → 模型创造性过高，容易"自由发挥"。

---

## 三、修复方案（架构师已决策 = A + C 组合）

### 3.1 决策点

| #决策点选择理由 |                          |                                          |
| ---------- | ------------------------ | ---------------------------------------- |
| **Q1 Prompt 强化还是换模型？** | ✅ **Prompt + temperature** | 换模型风险大、Prompt 风险小 |
| **Q2 temperature 设多少？** | ✅ **0.1** | 接近确定性输出，强制服从指令 |
| **Q3 是否升级 langchain4j？** | ❌ **否** | 升级 1.16+ 可能引入新 bug，风险/收益不划算 |

### 3.2 改动清单（3 处）

| #文件改动类型 |                                       |
| -------- | ---------------------------------------- |
| 1 | Nacos `del-cs-prompt.yml`：强化 Prompt（强制规则放最前） |
| 2 | `del-cs/.../config/ChatAutoConfig.java`：加 `.temperature(0.1).topP(0.7)` |
| 3 | `del-cs/.../service/ChatService.java`：把"强制调工具"日志加上 |

---

## 四、详细代码改动

### 改动 1：Nacos Prompt 强化

**推送位置**：Nacos `sakana` namespace / `DEFAULT_GROUP` / `del-cs-prompt.yml`

```yaml
# ★ BUG-017 强化版（2026-09-08）
# 解决：qwen-max 在简洁问题上跳过工具直接编答案
# 措施：
#   1. 强制规则放到 Prompt 最前面（视觉优先）
#   2. 增加"禁止凭知识回答"的明确禁令
#   3. 配合 ChatAutoConfig.temperature=0.1 强制服从指令

system_prompt: |
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ⚠️⚠️⚠️ 强制规则（不可违反）⚠️⚠️⚠️
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  1. **绝对禁止凭自己知识回答任何具体数据**
     - 菜品价格、热量、营养、库存 → 必须调 searchDishes 工具
     - 订单状态、订单详情、订单历史 → 必须调 getOrderDetail / getUserOrderHistory 工具
     - 你的训练数据可能过时，**只有工具返回的数据才是真实的**

  2. **任何涉及"具体菜品"或"具体订单"的问题，必须先调工具**
     - 即使问题看起来很简单（如"番茄鱼汤卡路里多少"），也必须先调工具
     - 不调工具就回答 = 编答案 = 错误行为

  3. **工具返回空列表 = 老实说没找到**
     - 不要编造、不要补全、不要"根据经验估计"

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  你的能力（只能做这三件事）
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. 推荐菜品（用 searchDishes 工具搜索商品）
  2. 查询单个订单详情（用 getOrderDetail 工具，传入 orderNo 业务订单号）
  3. 查询用户最近订单历史（用 getUserOrderHistory 工具）

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  调用示例
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  用户："番茄鱼汤卡路里多少" → 调 searchDishes(query="番茄鱼汤") → 从返回结果里找 calories
  用户："清淡的菜有哪些" → 调 searchDishes(query="清淡")
  用户："我最近的订单" → 调 getUserOrderHistory(maxResults=5)
  用户："我订单 ORD20260904TEST_REVIEW_03 详情" → 调 getOrderDetail(orderNo="ORD20260904TEST_REVIEW_03")

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  行为规则
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  - 不知道的事如实告知，不要瞎编
  - 简洁友好，回复不超过 100 字（除非用户明确要求列举）
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

### 改动 2：`ChatAutoConfig.java`

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\config\ChatAutoConfig.java`

```diff
     @Bean
     public QwenStreamingChatModel qwenStreamingChatModel() {
         log.info("[ChatAutoConfig] 初始化 QwenStreamingChatModel, model={}", modelName);
         return QwenStreamingChatModel.builder()
                 .apiKey(apiKey)
                 .modelName(modelName)
+                .temperature(0.1)  // ★ BUG-017：降低 LLM 创造性，强制服从 Prompt 指令
+                .topP(0.7)         // ★ BUG-017：配合 low temperature
                 .build();
     }
```

### 改动 3：`ChatService.java` 增强日志（可选）

**文件**：`E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\ChatService.java`

```diff
     @PostConstruct
     public void init() {
         log.info("[ChatService] 初始化 langchain4j Assistant...");
+        log.info("[ChatService] Prompt 长度={}，temperature=0.1, topP=0.7（BUG-017 强化）");
         ...
     }
```

---

## 五、验证步骤

### 5.1 后端验证

```powershell
# 步骤 1：修改 ChatAutoConfig.java，重启 del-cs

# 步骤 2：推送 Nacos Prompt 到 del-cs-prompt.yml
# （Nacos 配置自动刷新，无需重启）

# 步骤 3：curl 验证简洁问题是否调工具
$body = '{"message":"番茄鱼汤卡路里多少"}'
curl.exe -s -X POST "http://localhost:10011/api/v1/cs/chat" `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d $body `
  --max-time 30 | Select-String "type"

# 预期：能看到 1 个 tool 事件 + 多个 token 事件
```

### 5.2 验收标准

| #测试问题预期 |                          |
| ---- | ---------------------------- |
| 1 | "番茄鱼汤卡路里多少" | ✅ 必须有 tool 事件（searchDishes） |
| 2 | "清淡的菜有什么" | ✅ 必须有 tool 事件 |
| 3 | "番茄鱼汤多少钱" | ✅ 必须有 tool 事件 |
| 4 | "请帮我搜索 X" | ✅ 有 tool 事件 |
| 5 | "你好" | 🟢 无 tool 事件（直接回答） |
| 6 | "今天天气" | 🟢 无 tool 事件 |

### 5.3 关键日志

后端启动日志应包含：
```
[ChatAutoConfig] 初始化 QwenStreamingChatModel, model=qwen-max
[ChatService] 初始化 langchain4j Assistant...
[ChatService] Prompt 长度=xxx，temperature=0.1, topP=0.7（BUG-017 强化）
```

---

## 六、风险与回滚

| 风险应对 |                                       |
| ---- | ------------------------------------- |
| temperature=0.1 可能让回答太死板 | 🟡 监控 1 周，看用户反馈 |
| Prompt 过长影响 token 消耗 | 🟢 当前 ~600 字，可接受 |

**回滚方案**：把 temperature/topP 删掉，Prompt 改回 #BUG-014 强化版。

---

## 七、关联工单

- **#BUG-018**：订单查询类型不匹配（同步进行） — Prompt 中 getOrderDetail 的调用示例需更新为 `orderNo="..."`
