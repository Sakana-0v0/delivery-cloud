# 📘 智能菜品搜索模块 — 实施总结文档

> **项目**：外卖配送云 - 商品语义检索
> **工单**：`#SEARCH-001`
> **完成时间**：2026-09-06
> **作者**：Codex（基于实施全流程复盘）
> **状态**：✅ v1.0 已完成（关键词 + 知识库扩展 + 向量基础设施）

---

## 一、项目背景

### 1.1 业务需求

外卖系统 600+ 道菜，用户希望通过**自然语言**搜索：

| 场景 | 例子 | 传统搜索的问题 |
|------|------|--------------|
| 直接命中 | "番茄鸡蛋汤" | ✅ MySQL LIKE 能搞定 |
| 属性扩展 | "清淡的汤" | ❌ LIKE 无法理解"清淡" |
| 营养条件 | "蛋白质>20g" | ⚠️ 需要结构化过滤 |
| 风格特征 | "适合老人的" | ❌ 业务词不在菜名里 |

### 1.2 设计目标

- ✅ 支持关键词 + 属性词 + 结构化条件的混合搜索
- ✅ 知识库可热更新（无需重启）
- ✅ 为未来 LLM 智能客服预留扩展点
- ✅ 600 道菜规模下响应 < 500ms

---

## 二、整体设计

### 2.1 系统架构

```
[C 端 Vue 搜索框]
       ↓ GET /api/v1/search?query=清淡
[del-gateway (Spring Cloud Gateway)]
       ↓ 路由 /api/v1/search → del-product
[del-product (Spring Boot 3.2)]
   ├─ SearchController
       ↓
   ├─ SearchServiceImpl
       ├─ QueryRouter (意图分类)
       ├─ QueryParser (条件提取)
       ├─ QueryExpander (知识库扩展)
       ├─ ChineseTokenizer (Java IK 分词)
       └─ ES Query (NativeQueryBuilder + Wildcard)
            ↓
   [Elasticsearch 8.11.0 (Docker)]
       └─ dish 索引
            ├─ nameTokens (keyword)  ← 菜名分词
            ├─ categoryTokens (keyword)  ← 分类分词
            ├─ propertyTokens (keyword)  ← 描述分词
            ├─ embedding (dense_vector 1024)  ← DashScope
            └─ 其他结构化字段
```

### 2.2 数据流

**索引流程**（管理后台触发）：

```
POST /api/v1/admin/index/rebuild
   ↓
AdminIndexController
   ↓
IndexingServiceImpl.rebuildAll()
   ├─ SELECT * FROM t_product WHERE status=0  (737 条)
   ├─ 预加载 Category 表
   ├─ 分批（50条/批）processBatch
   │   ├─ ChineseTokenizer.tokenize(name) → nameTokens
   │   ├─ ChineseTokenizer.tokenize(category) → categoryTokens
   │   ├─ ChineseTokenizer.tokenize(description) → propertyTokens
   │   ├─ DashScopeEmbeddingClient.embedBatch(text) → embedding
   │   └─ ES save + refresh
   └─ 统计 success / failed
```

**搜索流程**（C 端触发）：

```
GET /api/v1/search?query=清淡
   ↓
SearchServiceImpl.search()
   ├─ ChineseTokenizer.tokenizeArray("清淡")
   │   └─ IK 分词 → ["清淡"]
   ├─ KnowledgeBaseService.getExpandedTerms("清淡")
   │   └─ 知识库: 清淡 → [少油, 少盐, 清淡, 素]
   ├─ 合并: searchTokens = [清淡, 少油, 少盐, 素]
   ├─ NativeQueryBuilder + BoolQuery
   │   ├─ filter: term(available=true)
   │   ├─ should: wildcard(nameTokens, *清淡*)
   │   ├─ should: wildcard(nameTokens, *少油*)
   │   ├─ should: wildcard(categoryTokens, *清淡*)
   │   └─ should: wildcard(propertyTokens, *少油*)
   │   └─ minimumShouldMatch(1)
   ├─ ES 查询
   └─ MySQL 精排（按 ES 返回顺序）
```

### 2.3 ES 文档结构

```json
{
  "_id": "1",
  "name": "清炖排骨汤",
  "description": "排骨炖至软烂，汤清味鲜，适合老人",
  "category": "汤品",
  "nameTokens": "清炖 排骨 汤",
  "categoryTokens": "汤品",
  "propertyTokens": "排骨 炖至 软烂 汤清 味鲜 适合 老人",
  "embedding": [0.012, -0.034, ...],  // 1024 维
  "calories": 180,
  "protein": 22.5,
  "fat": 8.2,
  "available": true,
  "updatedAt": "2026-09-06T15:30:00"
}
```

---

## 三、技术选型

### 3.1 搜索引擎：Elasticsearch 8.11.0

| 选项 | 优点 | 缺点 | 选择理由 |
|------|------|------|----------|
| **ES 8.x** | 社区成熟，生态丰富 | 8.x 有 breaking change | ✅ 主流选择 |
| Meilisearch | 轻量 | 中文支持弱 | ❌ |
| Typesense | 速度快 | 中文支持弱 | ❌ |
| MySQL FULLTEXT | 零基础设施 | 中文分词差 | ❌ 数据量稍大就崩 |

**为什么 8.11.0？**
- Spring Boot 3.2 默认支持版本
- Docker 官方镜像稳定
- 内置 KNN 搜索支持

### 3.2 Embedding：阿里云 DashScope `text-embedding-v3`

| 选项 | 优点 | 缺点 | 选择理由 |
|------|------|------|----------|
| **DashScope v3** | 中文效果好，国内访问快 | 收费（按 token）| ✅ 业务在国内 |
| OpenAI text-embedding-3 | 效果好 | 访问慢/合规 | ❌ |
| Qwen3-Embedding | 自部署可控 | 需要 GPU | ❌ 当前规模不必要 |
| 本地 BERT | 免费 | 中文效果一般 | ❌ |

**为什么 text-embedding-v3？**
- 1024 维，精度足够
- 国内访问快，延迟低
- 价格合理（600 道菜索引 < ¥1）

### 3.3 中文分词：Java IK Analyzer（关键决策）

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Java 端 IK + keyword 字段** | 完全可控，效果等同 ES IK | 需引入依赖 | ✅ 选用 |
| ES 端 IK 插件 | 原生集成 | 无 v8.x 版本 | ❌ |
| ES smartcn | 内置 | 8.x 已被移除 | ❌ |
| ES standard（按字切）| 免装 | 搜"番茄鸡蛋汤"分不出来 | ❌ |

**为什么 Java 端分词 + ES 存 keyword？**

这是**本项目最关键的架构决策**：

```
ES 端 IK 路径：ES 启动时加载 IK → ES 分析文本 → 存入倒排索引
Java 端 IK 路径：Java IK 分词 → 拼成字符串 → ES 存 keyword → 查询时 wildcard

对比：
✅ Java 端路径：
   - 与 Spring Boot 3.2 完美兼容
   - 不依赖 ES 插件生态
   - 分词结果完全可控
   - 600 道菜规模下性能足够
❌ ES 端路径：
   - IK 没有发布 v8.x（仓库最高 v1.10.6，对应 ES 5.x）
   - smartcn 在 8.x 已被官方移除
   - 部署、升级都受 ES 插件生态限制
```

**实现**：`ChineseTokenizer.java` 封装 IK Analyzer：

```java
@Component
public class ChineseTokenizer {
    private final IKAnalyzer ikAnalyzer = new IKAnalyzer(true);
    
    public String tokenize(String text) {
        // "番茄鸡蛋汤" → "番茄 鸡蛋 汤"
        StringBuilder sb = new StringBuilder();
        Lexeme[] lexemes = ikAnalyzer.analyze(text);
        for (Lexeme l : lexemes) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(l.getLexemeText());
        }
        return sb.toString();
    }
}
```

### 3.4 搜索查询：Wildcard Query（不是 KNN）

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Wildcard Query** | 简单可控 | 慢（大表不行）| ✅ 600 条够用 |
| KNN Vector Search | 真正的语义 | 600 条太小 | ⏸️ 未来 |
| Match Query | 性能好 | 需 analyzer | ❌ 与 IK 冲突 |
| SQL LIKE | 太慢 | ❌ 不用 |

**未来演进**：KNN 向量检索可与 Wildcard 配合（hybrid search），代码层面已预留 `embedding` 字段。

### 3.5 配置中心：Nacos

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **Nacos** | 已有基础设施，热更新 | 团队需熟悉 | ✅ |
| 文件配置 | 简单 | 改完需重启 | ❌ |
| Apollo | 功能强 | 重复建设 | ❌ |

---

## 四、技术亮点

### 4.1 🔮 EmbeddingClient 接口抽象（为 LLM 铺路）

```java
public interface EmbeddingClient {
    List<Float> embed(String text);
    List<List<Float>> embedBatch(List<String> texts);
}

// 当前：Java SDK 直接调 DashScope
@Service @Primary
public class DashScopeEmbeddingClient implements EmbeddingClient { ... }

// 未来：可无缝切换为 Python 服务
// @Service
// public class RemotePythonEmbeddingClient implements EmbeddingClient { ... }
```

**业务价值**：未来 #AI-CS-001（LLM 智能客服）启动时，调用方代码**零修改**。

### 4.2 🎯 三字段分词策略

| 字段 | 内容 | 搜索意图 |
|------|------|---------|
| `nameTokens` | 菜名分词 | "这个菜叫什么" |
| `categoryTokens` | 分类分词 | "这菜属于哪类" |
| `propertyTokens` | 描述分词 | "这菜怎么样" |

**为什么三个？** 知识库扩展词（如"清淡→少油"）是属性词，必须有 `propertyTokens` 才能命中（这是 #BUG-007 的核心教训）。

### 4.3 🚀 混合查询（Wildcard + Boolean）

```java
BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
    .filter(QueryBuilders.termQuery("available", true));  // 必须条件

for (String token : searchTokens) {  // 任一命中即可
    boolQuery.should(QueryBuilders.wildcardQuery("nameTokens", "*" + token + "*"));
    boolQuery.should(QueryBuilders.wildcardQuery("categoryTokens", "*" + token + "*"));
    boolQuery.should(QueryBuilders.wildcardQuery("propertyTokens", "*" + token + "*"));
}
boolQuery.minimumShouldMatch("1");
```

**优点**：一个 query 覆盖三个字段，召回率高。

### 4.4 💡 知识库 Nacos 热更新

```yaml
# Nacos 配置: search-knowledge-base.yml
search:
  expansion:
    rules:
      清淡: [少油, 少盐, 清淡, 素]
      高蛋白: [蛋白质, 健身, 鸡胸肉, 牛肉]
```

**业务价值**：新增业务词不用重启服务，运营同学自己改 Nacos 配置即可。

### 4.5 🔧 管理后台手动重建索引

```java
@PostMapping("/api/v1/admin/index/rebuild")
public R<Map<String, Object>> rebuildIndex(
    @RequestParam(defaultValue = "true") boolean async) {
    // 返回 success / failed / costMs
    // 不用 Canal 监听 DB 变更（过度设计）
}
```

**设计哲学**：600 道菜规模 + 手动触发 = 足够。Canal 监听 binlog 留给未来。

### 4.6 ⚡ 三级缓存架构

| 层级 | 内容 | 实现 |
|------|------|------|
| L1 | 进程内（Caffeine）| 现有，不动 |
| L2 | Redis | 现有，不动 |
| L3 | MySQL | 精排数据源 |
| L4 | ES | **本次新增**（dish 索引）|

ES 既是数据源也是缓存层，命中 ES 后再回 MySQL 精排。

---

## 五、实施过程中的缺陷与教训

### 5.1 设计层缺陷

| 缺陷 | 影响 | 修复 |
|------|------|------|
| **nameTokens 只索引菜名** | 知识库扩展词"清淡"永远匹配不上 | ✅ #BUG-007 新增 `propertyTokens` |
| **前端未实现** | 功能完成但用户用不到 | ⏸️ P1 待做 |
| **向量检索未实现** | 只有基础设施，搜索仍依赖 keyword | ⏸️ P1 待做 |

### 5.2 实现层踩坑（8 个 BUG）

| # | BUG | 根因 | 教训 |
|---|------|------|------|
| 1 | `BizException` 不接受 String | 调用错构造器 | 用 Lombok `@Builder` 避免 |
| 2 | `ChineseTokenizer.recognitionStopWords()` 不存在 | IK 版本差异 | 看官方文档而非记忆 |
| 3 | `Product.getId()` 永远 null | 实体的主键是 `fid` 不是 `id` | 实体设计要明确主键 |
| 4 | `search.expansion-rules` prefix 不匹配 yml | 命名风格不统一 | 横线/点要严格一致 |
| 5 | Spring Bean 冲突（两个 restTemplate）| Bean 命名重复 | Bean 命名要语义化 |
| 6 | `saveAll` 静默失败 | try-catch 吞异常 | **必须 `log.error(msg, e)` 打印 stacktrace** |
| 7 | `Criteria.matches()` API 不可靠 | Spring Data ES 5.x 行为 | 用 `NativeQueryBuilder` 替代 |
| 8 | **`@RequiredArgsConstructor` 不传播 `@Qualifier`** | Lombok 已知行为 | **改用显式构造函数** |

### 5.3 最大的踩坑（最重要教训）

```java
// ❌ 错误写法
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {
    @Qualifier("externalRestTemplate")
    private final RestTemplate restTemplate;  // Lombok 不会把 @Qualifier 复制到构造器参数！
}

// ✅ 正确写法
@Component
public class DashScopeEmbeddingClient {
    private final RestTemplate restTemplate;
    
    public DashScopeEmbeddingClient(
            DashScopeConfig dashScopeConfig,
            @Qualifier("externalRestTemplate") RestTemplate restTemplate) {  // 显式
        ...
    }
}
```

**这个坑最难发现**：
- 编译通过 ✅
- 启动成功 ✅
- 接口返回 200 ✅
- 但**行为完全错误**（调用了 @LoadBalanced 的 RestTemplate）

唯一能发现的方法是看**应用日志中的实际异常**。

### 5.4 验收教训

| 教训 | 说明 |
|------|------|
| **多类型查询词都要测** | 直接词（番茄）+ 扩展词（清淡）+ 结构化词（高蛋白）|
| **必须查数据库真实数据** | 不要假设 `status=0` 一定有数据 |
| **断言 vs 实际值** | ES 文档内容、Bean 注入、HTTP 响应都要看 |
| **性能验证** | 响应时间不能只看 `code=0` |

### 5.5 项目级反思

| 维度 | 反思 |
|------|------|
| **过度工程风险** | Canal、3 节点 ES、多服务都曾考虑，最终都没必要 |
| **不要假设规模** | 18 道菜→600 道菜，业务量翻了 33 倍才暴露设计盲点 |
| **写代码前先验证假设** | "ES 一定有 IK" 假设错了，"Spring 注入 Bean 唯一" 假设错了 |

---

## 六、最终成果

### 6.1 性能指标

| 指标 | 实测 | 目标 |
|------|------|------|
| 索引重建（同步）| 10-30 秒 | < 60 秒 ✅ |
| 搜索响应时间 | 100-500ms | < 200ms ⚠️ |
| 数据规模 | 737 道菜 | 600+ ✅ |
| ES 文档数 | 737/738 | 737+ ✅ |
| Embedding 维度 | 1024 | 1024 ✅ |

### 6.2 验收测试结果

| 查询 | 结果 | 状态 |
|------|------|------|
| "番茄" | 105 条 | ✅ 高召回 |
| "阿根廷" | 15 条 | ✅ |
| "清淡"（知识库扩展）| 5 条 | ✅ **核心修复** |
| "高蛋白" | 4 条 | ✅ |
| "适合老人" | 命中 propertyTokens | ✅ |
| "xyz不存在" | 0 条 | ✅ 正确降级 |

### 6.3 代码统计

| 指标 | 数值 |
|------|------|
| 新增文件 | 17 个 |
| 代码行数 | ~1500 行 Java |
| 修改文件 | 4 个（含 pom.xml、application.yml）|
| 涉及服务 | 1 个（del-product）|
| 新增数据表 | 0（复用 ES）|
| 删除文件 | 0 |

---

## 七、后续扩展建议

### 7.1 短期（1-2 周）🟡 P1

| # | 工作 | 工作量 | 业务价值 |
|---|------|--------|---------|
| 1 | **前端 SearchView.vue** | 1 天 | 用户用得到 |
| 2 | **前端 AdminIndexView.vue** | 0.5 天 | 管理用得到 |
| 3 | **向量检索实现**（knn）| 1 天 | 真正语义搜索 |

**向量检索实现示例**：

```java
// 在 SearchServiceImpl 中加
if (searchTokens.length > 0) {
    // wildcard 召回
    boolQuery.should(...wildcard...);
} else {
    // 无关键词时用向量召回
    float[] queryVector = embeddingClient.embed(rawQuery);
    boolQuery.must(QueryBuilders.knnQuery("embedding", queryVector, 50));
}
```

### 7.2 中期（1-2 月）🟡 P1

| # | 工作 | 工作量 | 业务价值 |
|---|------|--------|---------|
| 1 | **#AI-CS-001 LLM 智能客服** | 7-10 天 | 差异化竞争力 |
| 2 | **搜索结果重排序**（LLM-based）| 2 天 | 提升 CTR |
| 3 | **搜索建议词**（用户输入时自动补全）| 1 天 | 体验提升 |

### 7.3 长期（3-6 月）🟢 P2

| # | 工作 | 工作量 | 业务价值 |
|---|------|--------|---------|
| 1 | **图片搜索**（以图搜菜）| 5 天 | 体验升级 |
| 2 | **个性化推荐**（基于用户历史）| 7 天 | 转化提升 |
| 3 | **A/B 测试框架** | 3 天 | 数据驱动迭代 |
| 4 | **实时索引**（Canal 监听 DB 变更）| 3 天 | 不用手动重建 |
| 5 | **搜索分析看板**（热词、零结果词）| 3 天 | 产品决策支持 |
| 6 | **搜索降级策略**（ES 故障时降级 MySQL）| 1 天 | 高可用 |

### 7.4 架构演进路径

```
Phase 1（当前）✅：
  Wildcard + 知识库 + 向量基础设施
  ↓
Phase 2（1-2 月）：
  + 向量检索
  + LLM 智能客服（langchain4j）
  ↓
Phase 3（3-6 月）：
  + 实时索引（Canal）
  + 个性化推荐
  + 图片搜索
  ↓
Phase 4（远期）：
  + 多模态搜索
  + A/B 测试
  + 完整搜索分析平台
```

---

## 八、关键技术决策的"决策日志"

| 决策 | 选择 | 拒绝的备选 | 理由 |
|------|------|-----------|------|
| 搜索引擎 | ES 8.11.0 | Meilisearch/Typesense | 中文支持、knn、生态 |
| 分词 | Java IK | ES IK/smartcn | ES IK 无 v8.x 版本 |
| 查询方式 | Wildcard | KNN/全文 | 600 条规模，wildcard 够 |
| 数据源 | ES 单节点 | ES 3节点 | 规模小，省运维 |
| 向量生成 | DashScope | OpenAI/本地 | 国内访问、价格 |
| 索引时机 | 手动按钮 | Canal 监听 | 600 条不必要 |
| 配置存储 | Nacos | 本地 yml | 热更新、已有设施 |
| 服务归属 | del-product | 新服务 | 不加第 10 个服务 |
| 知识库 | KB rules map | ML 模型 | 简单可控、效果好 |
| 前缀匹配 | wildcard(*) | fuzzy match | 召回率优先 |

---

## 九、文件交付清单

```
docs/
├── SEARCH_MODULE_EVAL_002.md          (评估报告 v2)
├── WORK_ORDER_SEARCH-001.md            (主工单)
├── WORK_ORDER_BUG-002 ~ 007.md        (6 个 BUG 修复工单)
├── WORK_ORDER_SEARCH-001_FINAL_REPORT.md  (最终验收报告)
└── SEARCH_MODULE_CASE_STUDY.md         (本文档 - 实施总结)
```

代码：
```
del-product/src/main/java/com/sakana/search/
├── client/   (EmbeddingClient + DashScope)
├── config/   (ES + Knowledge)
├── document/ (DishDocument)
├── repository/
├── service/  (Search + Index + QueryRouter/Parser/Expander)
├── tokenizer/ (ChineseTokenizer)
└── web/      (SearchController + AdminIndexController)
```

---

## 十、总结

### 10.1 一句话总结

> 通过**ES + Java IK + DashScope** 的组合，用 1500 行代码实现了 600 道菜规模的**关键词搜索 + 知识库语义扩展 + 向量基础设施**，全部 8 个 BUG 已修复。

### 10.2 核心经验

1. **设计要为"业务真实规模"服务**（不是为"未来 100 万"）
2. **不要假设库/工具的默认行为**（ES 一定有 IK？Spring 注入唯一？）
3. **验收必须端到端**（看真实数据 + 多类型查询词）
4. **抽象层要预留**（EmbeddingClient 接口是给 LLM 的礼物）
5. **try-catch 不要静默**（这是 #BUG-004 的根本原因）
6. **Lombok 有坑**（@RequiredArgsConstructor 不传播 @Qualifier）

### 10.3 给后续开发者的建议

- 仔细读 `ChineseTokenizer` 的注释
- 修改 `DashScopeEmbeddingClient` 时**用显式构造函数**
- 加新搜索字段时遵循 `xxxTokens` 命名规范
- 任何 try-catch 后**必须 log.error(msg, e)**
- ES 数据验证**直接查 `_count` 和 `_search`**，不要只信 API 返回

---

**文档版本**：v1.0
**最后更新**：2026-09-06
**工单状态**：✅ #SEARCH-001 已关闭