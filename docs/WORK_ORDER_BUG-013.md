

---

# 工单 #BUG-013：SearchDishesTool 描述不精确，LLM 拒绝调用

> **创建时间**：2026-09-08
> **优先级**：🔴 **P0**（LLM 智能客服核心功能无法用）
> **接收方**：后端
> **预计工时**：30-60 分钟
> **依赖**：
>   - ✅ #BUG-012（内部端点已通）
>   - ✅ #SEARCH-001-V2（数据已补全）

---

## 一、问题陈述

用户问"番茄鱼汤卡路里多少？" → LLM 拒答，回复"系统无法提供"。

**Bug #BUG-012 修复后**：内部端点 ✓，但 LLM 仍不调用工具。

### 1.1 SSE 完整输出（实测）

```
data: {"type":"token","content":"目前"}
data: {"type":"token","content":"系统无法"}
data: {"type":"token","content":"提供"}
data: {"type":"token","content":"具体"}
data: {"type":"token","content":"菜品的卡路里数据"}
data: {"type":"token","content":"，建议您：\n- 查看商家页面"}
data: {"type":"token","content":"的营养信息（部分"}
data: {"type":"token","content":"餐厅会标注）  \n-"}
data: {"type":"token","content":" 或直接联系餐厅"}
data: {"type":"token","content":"客服咨询  \n\n需要我帮"}
data: {"type":"token","content":"您搜索"番茄鱼汤"}
data: {"type":"token","content":""相关菜品，"}
data: {"type":"token","content":"或推荐其他清淡低"}
data: {"type":"token","content":"脂的汤品吗？"}
data: {"type":"token","content":"😊"}
data: {"type":"done","content":""}
```

**没有 tool 事件**。LLM 直接编了一个"系统无法提供"的回复。

---

## 二、根因（已定位）

**`SearchDishesTool` 的 @Tool 描述错误**：

```java
// 当前实现
@Tool("搜索菜品，根据用户描述找到匹配的菜品。")
public List<Map<String, Object>> searchDishes(
    @P("用户想要搜索的菜品描述") String query) {
```

**LLM 的决策过程**：
1. 用户问"我刚吃的番茄鱼汤卡路里多少？"
2. LLM 看可用工具：`searchDishes` 描述是"搜索菜品"
3. **LLM 推理**：用户问的不是搜索，是问营养 → 工具不擅长
4. LLM 选择不调工具，直接告诉用户"无法提供"

**问题**：
- 描述只覆盖"搜索场景"
- 没说明工具返回的数据范围（名称、价格、营养、分类）
- 没说明这是查询菜品详细信息的**唯一**途径
- 返回 `List<Map<String,Object>>` → LLM 拿到 JSON 数组不确定数据形态

---

## 三、修复方案

### 改动 1：改 `@Tool` 描述

**文件**：`del-cs/src/main/java/com/sakana/cs/service/tools/SearchDishesTool.java`

```java
@Tool("查询菜品详细信息。可以用于搜索菜品、推荐菜品、查询价格/热量/营养成分、查询菜品分类等任何与具体菜品相关的问题。"
       + "【重要】涉及任何具体菜品的问题（价格、热量、营养、推荐等）都必须调用本工具，工具返回的数据是回答用户问题的唯一权威来源。"
       + "调用示例：用户问"番茄鱼汤卡路里多少？" → 工具调用 query=\"番茄鱼汤\" → 返回结果包含 calories 等信息")
public String searchDishes(
    @P("用户查询的菜品描述，可以是菜名、关键词、口味、场景等任意文本（用于在菜品数据库中搜索最相关的菜品）") String query) {
    log.info("[SearchDishesTool] query={}", query);
    try {
        R<SearchResponse> resp = searchFeign.search(query, 1, 5);
        if (resp != null && resp.getData() != null && resp.getData().getProducts() != null) {
            // ★ 改为 String 返回，把 List 序列化成 JSON 字符串
            // LLM 拿到 JSON 字符串后能直接解析和阅读
            return objectMapper.writeValueAsString(resp.getData().getProducts());
        }
    } catch (Exception e) {
        log.error("[SearchDishesTool] error={}", e.getMessage(), e);
    }
    return "[]";  // 空结果时返回空 JSON 数组字符串
}
```

### 改动 2：注入 ObjectMapper

**文件**：`SearchDishesTool.java`

```java
private final ProductSearchFeignClient searchFeign;
private final ObjectMapper objectMapper;  // 新增

public SearchDishesTool(ProductSearchFeignClient searchFeign, ObjectMapper objectMapper) {
    this.searchFeign = searchFeign;
    this.objectMapper = objectMapper;
}
```

### 改动 3：更新 Nacos Prompt

**文件**：Nacos 配置 `del-cs-prompt.yml`

```yaml
cs:
  system-prompt: |
    你是外卖系统智能客服"小饿"。
    
    你的核心能力：**调用工具查询菜品数据**。
    
    必须遵守的规则：
    1. 任何关于菜品的问题（价格、热量、营养、口味、推荐、分类、是否有某菜品等）**都必须调用** searchDishes 工具
    2. 工具返回的是数据库真实数据，是回答用户问题的**唯一权威来源**
    3. 如果工具返回空数组（[]），说明没有匹配的菜品，**不要编造菜品信息**
    4. 如果工具调用失败，回复"暂时无法查询菜品数据，请稍后重试"
    5. 不知道的事**如实告知**，不要瞎编
    
    可用工具：
    - searchDishes(query): 查询菜品（含名称、价格、营养、分类等所有信息）
    
    不要试图在没有调用工具的情况下回答菜品相关问题。
```

---

## 四、验证清单

### 4.1 后端编译

```bash
mvn clean compile -pl del-cs -am -DskipTests
```

### 4.2 重启 del-cs + 推 Nacos Prompt

```bash
# 重启 del-cs
# IDEA 里 Restart

# Nacos 修改 del-cs-prompt.yml 后会自动推送
# 验证 prompt 已更新：
curl -X GET "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=del-cs-prompt.yml&group=DEFAULT_GROUP" \
  -H "Authorization: Bearer $TOKEN"
```

### 4.3 关键测试用例

| 用户问 | 修复前 | 修复后（预期）|
|--------|--------|-------------|
| "番茄鱼汤卡路里多少？" | ❌ "系统无法提供" | ✅ "约 150 千卡，蛋白 8g" |
| "有什么清淡的汤" | ✅ 5 条 | ✅ 5 条（不变）|
| "番茄鱼汤多少钱" | ⚠️ 模糊 | ✅ "番茄鱼汤 15 元" |
| "牛肉类推荐下" | ⚠️ 模糊 | ✅ "阿根廷烤牛排 78 元，350 千卡" |
| "这个菜是哪里菜" | ⚠️ 模糊 | ✅ "阿根廷烤牛排是西式主菜" |
| "能推荐点素菜吗" | ❌ 编的 | ✅ "有 X 道素菜：..." |

### 4.4 curl 测试

```bash
USER_TOKEN=$(curl -s -X POST http://localhost:10010/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sakana","password":"123456"}' | jq -r .data.accessToken)

curl -X POST http://localhost:10010/api/v1/cs/chat \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"番茄鱼汤卡路里多少"}'
# 预期：tool 事件 + 包含卡路里数字的回复
```

**关键指标**：响应里**必须**出现 `data: {"type":"tool"...` 事件。如果只有 `token` 事件没 `tool` 事件，说明 LLM 还没被说服调工具。

---

## 五、根因复盘（重要！）

```
❌ 我之前的失误：
  - 看到 #BUG-012 验证 6/6 通过就以为 OK
  - 但 6/6 是测"工具调用是否成功"，不是测"LLM 是否调用工具"
  - 用户实际问"卡路里"我没真测过

✅ 这次教训：
  - 任何 LLM 功能验证必须看完整 SSE 输出（含 tool 事件）
  - 不能只看 HTTP 200
  - 不能只看"工具返回 5 条"
  - 必须验证"LLM 真的用了工具结果生成回复"
```

---

## 六、关联工单

| 工单 | 状态 | 备注 |
|------|------|------|
| #BUG-012（内部端点）| ✅ | 修了 403，但 Tool 设计这个 bug 暴露出来了 |
| **#BUG-013（本工单）**| 🔴 | 修 Tool 设计 + Prompt |
| #AI-CS-001-MVP | ⏸️ 待重新验收 | Tool 修完才能真正验收 |

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 复现 + 根因 | Codex | 2026-09-08 |
| 工单创建 | Codex | 2026-09-08 |
| 后端修复 | _待_ | 30 分钟 |
| Nacos Prompt 更新 | _待_ | 10 分钟 |
| 端到端验收 | _待_ | 10 分钟 |

---

## 八、给我（Codex 自己）的教训

> **LLM 应用验证必须看完整端到端，不能看 HTTP 200 就当通过**

下次验证 LLM 功能前，我会要求：
1. 实际触发完整业务场景（如问"卡路里"）
2. 看 SSE 完整输出（含 tool 事件 + 最终回复）
3. 检查回复内容是否真的用了工具返回的数据
4. 不能只看 HTTP 状态码和"工具调用成功"
