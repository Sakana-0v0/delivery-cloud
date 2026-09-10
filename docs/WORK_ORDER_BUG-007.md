# 📋 工单 #BUG-007：nameTokens 与扩展词语义不匹配（设计层缺陷）

> **创建时间**：2026-09-06
> **优先级**：🔴 **P0**（搜索核心功能"语义扩展"实际未生效）
> **接收方**：后端工程师
> **预计工时**：3 小时
> **依赖工单**：#BUG-004（✅）、#BUG-005（✅ 代码层）、#BUG-006（🔴 待修）

---

## 一、问题描述

### 1.1 现象

| ES 真实测试 | 结果 | 原因 |
|------------|------|------|
| 搜 `*阿根廷*` | ✅ 14 条 | nameTokens 含"阿根廷" |
| 搜 `*清淡*` | ❌ 0 条 | nameTokens **不含**"清淡"（属性词）|
| 搜 `*少油*` | ❌ 0 条 | 同上 |

### 1.2 根本原因

`nameTokens` 字段**只索引了菜名分词**，但知识库扩展词（清淡/少油/少盐/素）是**属性词**（描述菜"怎么样"），不在菜名中。

```
t_product.name        = "阿根廷牛肉馅饼"
t_product.description = "外皮酥脆 牛肉鲜嫩 适合老人"   ← 属性信息在这里
                ↓
nameTokens = "阿根廷 牛肉 馅饼"                    ← 只取了菜名
                ↓
搜索 "清淡" → nameTokens 不含"清淡" → 0 条
```

无论 BUG-005 怎么修，**搜"清淡"都不会返回商品**，因为菜名里就没有这个词。

### 1.3 实际影响

| 功能 | 状态 |
|------|------|
| 关键词命中（菜名中有的词）| ✅ 工作 |
| 知识库语义扩展（清淡→少油）| ❌ **永远不工作** |
| 真正的"语义搜索" | ❌ 失效（依赖 BUG-006 修复）|

---

## 二、修复方案

### 2.1 推荐方案：新增 `propertyTokens` 字段

把描述（description）也加入搜索字段，但用**独立字段**保持语义清晰。

**改动 1**：`DishDocument.java` 新增字段

```java
@Field(type = FieldType.Keyword)
private String propertyTokens;   // "外皮 酥脆 牛肉 鲜嫩 适合 老人"
```

**改动 2**：`IndexingServiceImpl.java` processBatch 中新增分词

```java
// 原代码
doc.setNameTokens(chineseTokenizer.tokenize(p.getName()));
doc.setCategoryTokens(chineseTokenizer.tokenize(categoryName));

// ★ 新增
doc.setPropertyTokens(chineseTokenizer.tokenize(p.getDescription()));
```

**改动 3**：`SearchServiceImpl.java` 搜索逻辑同时查两个字段

```java
// 原代码
for (String token : searchTokens) {
    boolBuilder.should(QueryBuilders.wildcard(w -> w
            .field("nameTokens").value("*" + token + "*")));
    boolBuilder.should(QueryBuilders.wildcard(w -> w
            .field("categoryTokens").value("*" + token + "*")));
}

// ★ 新增
for (String token : searchTokens) {
    boolBuilder.should(QueryBuilders.wildcard(w -> w
            .field("propertyTokens").value("*" + token + "*")));
}
```

### 2.2 重建后效果

| 搜索词 | 之前 | 之后 |
|--------|------|------|
| "阿根廷" | ✅ 14 条 | ✅ 14 条 |
| "清淡" | ❌ 0 条 | ✅ N 条（description 含"少油少盐"的）|
| "少油" | ❌ 0 条 | ✅ N 条 |
| "适合老人" | ❌ 0 条 | ✅ N 条 |
| "汤" | ✅ 9 条 | ✅ 9 条（菜名/分类都有）|

### 2.3 字段设计原则

| 字段 | 索引内容 | 用途 |
|------|---------|------|
| `nameTokens` | 菜名分词 | "这个菜叫什么" |
| `categoryTokens` | 分类分词 | "这菜属于哪类" |
| `propertyTokens`（新）| 描述分词 | "这菜是什么特点" |
| `embedding`（BUG-006 修复后）| 1024 维向量 | 真正的语义搜索 |

---

## 三、详细改动

### 3.1 字段添加

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\document\DishDocument.java`

```diff
  // ============ ★ Java 端 IK 分词结果（Wildcard 查询依赖） ============
  @Field(type = FieldType.Keyword)
  private String nameTokens;       // "番茄 鸡蛋 汤"
  
  @Field(type = FieldType.Keyword)
  private String categoryTokens;   // "汤品"
  
+ // ============ ★ BUG-007：描述分词（属性词搜索） ============
+ @Field(type = FieldType.Keyword)
+ private String propertyTokens;   // "外皮 酥脆 牛肉 鲜嫩 适合 老人"
```

### 3.2 索引时填充

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\service\impl\IndexingServiceImpl.java`

```diff
  // Java 端 IK 分词
  doc.setNameTokens(chineseTokenizer.tokenize(p.getName()));
  doc.setCategoryTokens(chineseTokenizer.tokenize(categoryName));
+ // BUG-007：描述分词（用于属性词搜索）
+ String description = Optional.ofNullable(p.getDescription()).orElse("");
+ doc.setPropertyTokens(chineseTokenizer.tokenize(description));
```

### 3.3 搜索时匹配

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\service\impl\SearchServiceImpl.java`

```diff
  if (searchTokens.length > 0) {
      for (String token : searchTokens) {
          boolBuilder.should(QueryBuilders.wildcard(w -> w
                  .field("nameTokens").value("*" + token + "*")));
          boolBuilder.should(QueryBuilders.wildcard(w -> w
                  .field("categoryTokens").value("*" + token + "*")));
+         // BUG-007：同时搜索描述分词
+         boolBuilder.should(QueryBuilders.wildcard(w -> w
+                 .field("propertyTokens").value("*" + token + "*")));
      }
      boolBuilder.minimumShouldMatch("1");
  }
```

### 3.4 重建索引（必须，因为 ES 已有索引的 mapping 不含新字段）

```bash
# 删除旧索引（mapping 不会自动加新字段）
curl -X DELETE "http://localhost:9200/dish"

# 重建（会按 DishDocument 的新 mapping 自动创建）
curl -X POST "http://localhost:10010/api/v1/admin/index/rebuild?async=false" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
# 预期：{"success": 737, "failed": 0}
```

---

## 四、验证清单

### 4.1 ES 验证

```bash
# 1. 验证 propertyTokens 字段已创建
curl -s "http://localhost:9200/dish/_mapping?pretty" 2>&1 | grep propertyTokens
# 预期：看到 propertyTokens 字段定义

# 2. 验证文档有 propertyTokens 内容
curl -s "http://localhost:9200/dish/_doc/1" | python -c "
import json, sys
data = json.loads(sys.stdin.read())
print('nameTokens:', data.get('_source', {}).get('nameTokens'))
print('propertyTokens:', data.get('_source', {}).get('propertyTokens'))"
# 预期：两个字段都有值
```

### 4.2 搜索验证

```bash
# 1. 搜"阿根廷"（菜名直接命中）
curl "http://localhost:10010/api/v1/search?query=%E9%98%BF%E6%A0%B9%E5%BB%B7" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：14 条（命中 nameTokens）

# 2. 搜"清淡"（属性词 → 知识库扩展）
curl "http://localhost:10010/api/v1/search?query=%E6%B8%85%E6%B7%A1" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：> 0 条（命中 propertyTokens）

# 3. 搜"高蛋白"（知识库扩展词）
curl "http://localhost:10010/api/v1/search?query=%E9%AB%98%E8%9B%8B%E7%99%BD" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：> 0 条

# 4. 搜"适合老人"
curl "http://localhost:10010/api/v1/search?query=%E9%80%82%E5%90%88%E8%80%81%E4%BA%BA" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：> 0 条
```

### 4.3 知识库扩展真正生效

```bash
# 查 Nacos 配置 search.expansion.rules 应有：
# 清淡 → [少油, 少盐, 清淡, 素]
# 高蛋白 → [蛋白质, 健身, 鸡胸肉, 牛肉]

# 搜"清淡"应能命中（通过扩展词 "少油" 命中 description 含"少油少盐"的菜品）
```

---

## 五、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| description 为空 | 🟢 低 | 用 `Optional.ofNullable().orElse("")` 兜底 |
| 分词词数过多 | 🟢 低 | description 一般 < 100 词，分词后 < 200 token |
| ES 索引重建耗时 | 🟡 中 | 重建 737 条 + 3 倍分词 ≈ 30-60 秒 |
| 与 BUG-006 联动 | 🟢 低 | 修复不冲突，可独立执行 |

---

## 六、与 #BUG-006 的关系

| 维度 | #BUG-007（本工单）| #BUG-006 |
|------|------------------|----------|
| 修复什么 | 关键词 + 属性词匹配 | 向量召回（语义搜索）|
| 搜索能力 | 关键词扩展 | 真正的语义相似度 |
| 覆盖场景 | "清淡"（属性词）| "想吃点像火锅但不要太辣" |
| 优先级 | P0 | P0 |
| 可独立修 | ✅ 是 | ✅ 是 |

**建议**：
1. 先修 #BUG-007（1 行 × 3 个文件，工作量小）
2. 再修 #BUG-006（RestTemplate 注入）
3. 两个都修后，"清淡"既能用 wildcard 命中（通过 propertyTokens + 知识库扩展），也能用 embedding 做向量召回

---

## 七、对 #SEARCH-001 状态的影响

| 工单 | 状态 |
|------|------|
| #SEARCH-001 | ⚠️ 暂不关闭（需 #BUG-006 + #BUG-007 都修完）|
| #BUG-004 | ✅ 已关闭 |
| #BUG-005 | ✅ 已关闭（代码层） |
| #BUG-006 | 🔴 待修 |
| **#BUG-007**（本工单）| 🔴 **待修** |

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 根因定位 | Codex | 2026-09-06 |
| 修复 | _待后端工程师_ | |
| 验收 | _待填_ | |

---

**修复命令**（最快路径）：
1. `DishDocument.java` 加 `propertyTokens` 字段
2. `IndexingServiceImpl.java` 加分词逻辑
3. `SearchServiceImpl.java` 加 wildcard 查询
4. 删除 ES 旧索引
5. 触发重建
6. 跑验证清单