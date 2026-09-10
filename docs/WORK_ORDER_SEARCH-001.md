# 📋 工单 #SEARCH-001：智能菜品搜索模块（Java 端 IK 分词 + ES + DashScope Embedding）

> **创建时间**：2026-09-05
> **最后更新**：2026-09-05（改为 Java 端分词方案）
> **优先级**：P0（业务需求）
> **接收方**：后端工程师（Java）+ 前端工程师
> **预计工时**：8 天
> **设计依据**：`docs/SEARCH_MODULE_EVAL_002.md`

---

## ⚠️ 重要变更（2026-09-05）

**原方案**（2026-09-05 初版）：ES 端 IK 插件

**当前方案**：Java 端 IK Analyzer（避开 ES 插件兼容性坑）

### 变更原因

| 方案 | 问题 |
|------|------|
| ES 8.x IK 插件 | IK 没有发布 v8.x 版本（infinilabs 最高到 v1.10.6，对应 ES 5.x）|
| ES 8.x smartcn | 已被官方移除 |
| Java 端 IK | ✅ 与 Spring Boot 3.2 完美兼容，中文分词效果等同 ES 端 IK |

### 核心思路

```
原方案：ES 端依赖 IK 插件分词
当前方案：Java 进程内分词，把分词结果存到 ES 的 keyword 字段
```

---

## 一、已确认的关键决策

| 决策点 | 选择 |
|--------|------|
| **菜品规模** | 600+ 道菜 |
| **Embedding** | DashScope 云端 API（Java SDK） |
| **embedding 时机** | 管理后台"重建索引"按钮触发 |
| **ES 部署** | Docker 单节点 |
| **服务归属** | 全部在 `del-product` |
| **知识库存储** | Nacos 配置中心 |
| **中文分词** | **Java 端 IK Analyzer（wltea 2012_u6）** |
| **ES Mapping** | keyword 字段存分词结果，wildcard 查询 |
| **数据同步** | 不用 Canal |

---

## 二、整体架构

```
[C 端 Vue 搜索框]
       ↓ GET /api/v1/search?query=...
[del-product SearchController]
       ↓
[SearchServiceImpl]
   ├─ ChineseTokenizer.tokenize(query)        ← Java 端 IK 分词
   ├─ ES Wildcard Query (nameTokens 字段)
   └─ MySQL 精排

[管理后台] → [AdminIndexController]
       ↓ 触发"重建索引"
[IndexingService]
   ├─ 全量扫描 t_product
   ├─ ChineseTokenizer.tokenize(name)         ← Java 端 IK 分词
   ├─ EmbeddingClient.embedBatch()
   └─ 批量索引到 ES (dishDocument)
```

---

## 三、任务分派

### ☕ 后端工程师（6 天）

#### 3.1 文件改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `del-product/pom.xml` | 修改 | 加 IK Analyzer + ES 依赖 |
| `del-product/src/main/resources/application.yml` | 修改 | ES 配置 |
| `del-product/src/main/java/com/sakana/search/tokenizer/ChineseTokenizer.java` | **新建** | 🔮 IK 分词器封装 |
| `del-product/src/main/java/com/sakana/search/config/ElasticsearchConfig.java` | 新建 | ES 客户端配置 |
| `del-product/src/main/java/com/sakana/search/document/DishDocument.java` | 新建 | ES 文档实体 |
| `del-product/src/main/java/com/sakana/search/repository/DishSearchRepository.java` | 新建 | Spring Data ES Repo |
| `del-product/src/main/java/com/sakana/search/service/SearchService.java` | 新建 | 接口 |
| `del-product/src/main/java/com/sakana/search/service/impl/SearchServiceImpl.java` | 新建 | 核心搜索逻辑 |
| `del-product/src/main/java/com/sakana/search/service/QueryRouter.java` | 新建 | 意图路由 |
| `del-product/src/main/java/com/sakana/search/service/QueryParser.java` | 新建 | 条件提取 |
| `del-product/src/main/java/com/sakana/search/service/QueryExpander.java` | 新建 | 知识库扩展 |
| `del-product/src/main/java/com/sakana/search/service/KnowledgeBaseService.java` | 新建 | 从 Nacos 读 KB |
| `del-product/src/main/java/com/sakana/search/service/IndexingService.java` | 新建 | 索引重建 |
| `del-product/src/main/java/com/sakana/search/client/EmbeddingClient.java` | 新建 | 🔮 接口抽象 |
| `del-product/src/main/java/com/sakana/search/client/DashScopeEmbeddingClient.java` | 新建 | 当前实现 |
| `del-product/src/main/java/com/sakana/search/web/SearchController.java` | 新建 | C 端搜索 API |
| `del-product/src/main/java/com/sakana/search/web/admin/AdminIndexController.java` | 新建 | 管理后台重建索引按钮 |
| `del-product/src/main/java/com/sakana/search/dto/SearchRequest.java` | 新建 | 请求 DTO |
| `del-product/src/main/java/com/sakana/search/dto/SearchResponse.java` | 新建 | 响应 DTO |
| `del-product/src/main/java/com/sakana/search/dto/IndexTask.java` | 新建 | 索引任务 DTO |

#### 3.2 Maven 依赖

```xml
<dependencies>
    <!-- Elasticsearch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
    </dependency>
    
    <!-- DashScope SDK -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>dashscope-sdk-java</artifactId>
        <version>2.20.0</version>
    </dependency>
    
    <!-- ★ IK Analyzer Java 版（替代 ES 端 IK 插件）-->
    <dependency>
        <groupId>org.wltea</groupId>
        <artifactId>ikanalyzer</artifactId>
        <version>2012_u6</version>
    </dependency>
</dependencies>
```

#### 3.3 application.yml 配置

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 30s

dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  embedding-model: text-embedding-v3
  embedding-dim: 1024

nacos:
  data-id: search-knowledge-base.yml
  group: DEFAULT_GROUP
```

#### 3.4 ★ ChineseTokenizer 实现（核心）

**文件**：`del-product/src/main/java/com/sakana/search/tokenizer/ChineseTokenizer.java`

```java
package com.sakana.search.tokenizer;

import org.springframework.stereotype.Component;
import org.wltea.analyzer.core.IKAnalyzer;
import org.wltea.analyzer.core.Lexeme;

import java.io.IOException;

/**
 * 中文分词器（基于 IK Analyzer Java 版）
 *
 * <p>取代 ES 端 IK 插件，分词在 Java 进程内完成。
 * <p>输入"番茄鸡蛋汤" → 输出"番茄 鸡蛋 汤"
 */
@Component
public class ChineseTokenizer {

    private final IKAnalyzer ikAnalyzer = new IKAnalyzer(true); // true=智能分词

    /**
     * 对文本进行中文分词，返回空格分隔的词条
     */
    public String tokenize(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        try {
            Lexeme[] lexemes = ikAnalyzer.analyze(text);
            for (int i = 0; i < lexemes.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(lexemes[i].getLexemeText());
            }
        } catch (IOException e) {
            throw new RuntimeException("分词失败: " + text, e);
        }
        return sb.toString().trim();
    }

    /**
     * 分词返回词条数组（用于搜索时构造 wildcard）
     */
    public String[] tokenizeArray(String text) {
        if (text == null || text.trim().isEmpty()) return new String[0];
        try {
            Lexeme[] lexemes = ikAnalyzer.analyze(text);
            String[] result = new String[lexemes.length];
            for (int i = 0; i < lexemes.length; i++) {
                result[i] = lexemes[i].getLexemeText();
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("分词失败: " + text, e);
        }
    }
}
```

#### 3.5 DishDocument 设计

**文件**：`del-product/src/main/java/com/sakana/search/document/DishDocument.java`

```java
@Data
@Document(indexName = "dish")
public class DishDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Integer)
    private Integer calories;

    @Field(type = FieldType.Float)
    private Float protein;

    @Field(type = FieldType.Float)
    private Float fat;

    @Field(type = FieldType.Boolean)
    private Boolean available;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    // ============ ★ Java 端 IK 分词结果 ============
    @Field(type = FieldType.Keyword)
    private String nameTokens;       // "番茄 鸡蛋 汤"

    @Field(type = FieldType.Keyword)
    private String categoryTokens;   // "汤品"

    // ============ ★ Embedding 向量 ============
    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private List<Float> embedding;
}
```

#### 3.6 IndexingService 实现

```java
@Service
@Slf4j
public class IndexingService {

    @Autowired private ProductMapper productMapper;
    @Autowired private DishSearchRepository dishRepo;
    @Autowired private EmbeddingClient embeddingClient;
    @Autowired private ChineseTokenizer chineseTokenizer;  // ★ 新增

    public IndexTask rebuildAll() {
        IndexTask task = new IndexTask();
        task.setStartTime(LocalDateTime.now());

        List<Product> products = productMapper.selectList(
            new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)
        );

        List<DishDocument> docs = new ArrayList<>();
        List<String> textsForEmbedding = new ArrayList<>();

        for (Product p : products) {
            DishDocument doc = new DishDocument();
            doc.setId(p.getId());
            doc.setName(p.getName());
            doc.setDescription(p.getDescription());
            doc.setCategory(p.getCategory());
            doc.setAvailable(p.getStatus() == 1);
            doc.setUpdatedAt(LocalDateTime.now());

            // ★ Java 端 IK 分词（核心）
            doc.setNameTokens(chineseTokenizer.tokenize(p.getName()));
            doc.setCategoryTokens(chineseTokenizer.tokenize(p.getCategory()));

            docs.add(doc);
            textsForEmbedding.add(p.getName() + " " + p.getCategory() + " " +
                Optional.ofNullable(p.getDescription()).orElse(""));
        }

        // 批量 embedding
        List<List<Float>> embeddings = embeddingClient.embedBatch(textsForEmbedding);
        for (int i = 0; i < docs.size(); i++) {
            docs.get(i).setEmbedding(embeddings.get(i));
        }

        // 批量索引到 ES
        dishRepo.saveAll(docs);

        task.setTotal(products.size());
        task.setSuccess(products.size());
        task.setEndTime(LocalDateTime.now());
        return task;
    }
}
```

#### 3.7 SearchServiceImpl 核心（Wildcard 查询）

```java
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired private DishSearchRepository dishRepo;
    @Autowired private ElasticsearchOperations elasticsearch;
    @Autowired private ProductMapper productMapper;
    @Autowired private QueryRouter queryRouter;
    @Autowired private QueryParser queryParser;
    @Autowired private QueryExpander queryExpander;
    @Autowired private ChineseTokenizer chineseTokenizer;  // ★ 新增

    public SearchResponse search(String rawQuery, int page, int size) {
        // 1. 意图路由
        QueryIntent intent = queryRouter.route(rawQuery);

        // 2. 解析查询条件
        ParsedQuery parsed = queryParser.parse(rawQuery);

        // 3. 知识库扩展
        List<String> expandedTerms = queryExpander.expand(rawQuery);

        // 4. ★ Java 端分词
        String[] tokens = chineseTokenizer.tokenizeArray(rawQuery);

        // 5. 构造 ES 查询（Wildcard 匹配 nameTokens）
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("available", true));

        if (tokens.length > 0) {
            BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();
            for (String token : tokens) {
                shouldQuery.should(QueryBuilders.wildcardQuery("nameTokens", "*" + token + "*"));
                shouldQuery.should(QueryBuilders.wildcardQuery("categoryTokens", "*" + token + "*"));
            }
            boolQuery.must(shouldQuery);
        }

        // 6. 结构化过滤（营养等）
        if (parsed.hasNutritionFilter()) {
            boolQuery.filter(QueryBuilders.rangeQuery("calories").lte(parsed.getCaloriesMax()));
        }

        // 7. ES 查询
        NativeSearchQuery esQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        SearchHits<DishDocument> hits = elasticsearch.search(esQuery, DishDocument.class);

        // 8. MySQL 精排
        List<Long> ids = hits.stream().map(h -> h.getContent().getId()).collect(Collectors.toList());
        List<Product> products = ids.isEmpty() ? List.of() : productMapper.selectBatchIds(ids);

        return new SearchResponse(products, hits.getTotalHits());
    }
}
```

#### 3.8 EmbeddingClient 接口（🔮 为未来 LLM 预留）

```java
public interface EmbeddingClient {
    List<Float> embed(String text);
    List<List<Float>> embedBatch(List<String> texts);
}

@Service
@Primary
public class DashScopeEmbeddingClient implements EmbeddingClient {
    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.embedding-model}")
    private String model;

    @Override
    public List<Float> embed(String text) {
        try {
            EmbeddingResponse resp = TextEmbedding.builder()
                    .text(text)
                    .model(model)
                    .apiKey(apiKey)
                    .build()
                    .invoke();

            float[] vector = resp.getOutput().getEmbeddings().get(0).getEmbedding();
            List<Float> result = new ArrayList<>(vector.length);
            for (float f : vector) result.add(f);
            return result;
        } catch (Exception e) {
            throw new BizException("Embedding 生成失败: " + e.getMessage());
        }
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 25) {
            List<String> batch = texts.subList(i, Math.min(i + 25, texts.size()));
            try {
                List<List<String>> batchInput = batch.stream()
                        .map(List::of)
                        .collect(Collectors.toList());
                MultiDocumentEmbeddingResponse resp = TextEmbedding.batchCall(
                        new MultiDocumentEmbeddingRequest(model, batchInput),
                        apiKey, null);
                for (var output : resp.getOutput().getEmbeddings()) {
                    float[] vec = output.getEmbedding();
                    List<Float> result = new ArrayList<>(vec.length);
                    for (float f : vec) result.add(f);
                    results.add(result);
                }
            } catch (Exception e) {
                throw new BizException("Embedding 批量失败: " + e.getMessage());
            }
        }
        return results;
    }
}
```

#### 3.9 知识库 Nacos 配置

`search-knowledge-base.yml`：

```yaml
expansion_rules:
  清淡:
    terms: [少油, 少盐, 清淡, 素]
  软烂:
    terms: [炖, 煮, 烂, 软, 适合老人]
  潮汕:
    terms: [潮汕, 粤菜, 闽菜]
  高蛋白:
    min_protein: 20
    terms: [蛋白质, 健身]
```

### 🎨 前端工程师（1 天）

#### 3.10 文件改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/views/user/SearchView.vue` | 新建 | C 端搜索页 |
| `src/components/SearchBox.vue` | 新建 | 搜索框组件 |
| `src/components/SearchResult.vue` | 新建 | 搜索结果展示 |
| `src/api/search.ts` | 新建 | API 封装 |
| `src/router/index.ts` | 修改 | 新增搜索路由 |
| `src/views/admin/AdminIndexView.vue` | 新建 | 管理后台"重建索引"按钮 |

### 🛠️ 运维工程师（0.5 天）

#### 3.11 ES Docker 部署（简化版）

`docker-compose.yml`：

```yaml
# 简化版：只有 ES 一个容器，del-product 在 IDEA 直跑
version: '3.8'

services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    container_name: es-search
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      - cluster.name=delivery-search
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9200/_cluster/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  es_data:
    driver: local
```

**说明**（已与用户确认）：
- ❌ 不需要 `networks` 配置（只有 ES 一个容器，其他服务在 IDEA 直跑）
- ✅ `del-product` 通过 `http://localhost:9200` 访问 ES
- ✅ 未来如把 `del-product` 也 Docker 化，再加 networks 配置

---

## 四、API 设计

### 4.1 C 端搜索 API

```java
@GetMapping("/api/v1/search")
public R<SearchResponse> search(
    @RequestParam String query,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
);
```

### 4.2 管理后台索引 API

```java
@PostMapping("/api/v1/admin/index/rebuild")
public R<IndexTask> rebuildIndex();

@GetMapping("/api/v1/admin/index/status")
public R<IndexTask> getLastTask();
```

---

## 五、降级方案

| 场景 | 降级行为 |
|------|---------|
| ES 不可用 | 降级到 MySQL LIKE + 营养字段过滤 |
| DashScope API 失败 | 重试 3 次 → 报错给管理员 |
| 知识库未配置 | 跳过扩展步骤，纯 wildcard 检索 |

---

## 六、🔮 AI 能力未来演进路径

本工单的产出将被未来的 **LLM 智能客服**（`#AI-CS-001`）复用：

### 6.1 复用清单

| SEARCH-001 产出 | LLM 智能客服怎么用 |
|----------------|-------------------|
| `ChineseTokenizer` | LLM 的 RAG 检索前可对查询分词 |
| ES `dish` 索引 | **RAG 检索**（特色菜推荐）|
| `GET /api/v1/search` | **Tool 调用** |
| `EmbeddingClient` 接口 | 抽象层，Phase 2 可换 Python 实现 |
| Nacos 配置中心 | 复用，LLM 的 Prompt 也存这里 |

### 6.2 未来演进路径

```
Phase 1 (当前 SEARCH-001)
  ├─ Java 端 IK 分词（ChineseTokenizer）
  ├─ Java SDK 直接调 DashScope
  ├─ EmbeddingClient 接口（已预留）
  └─ del-product 暴露 REST REST

Phase 2 (LLM 智能客服, 预计 6 个月后)
  ├─ 新建服务 del-cs（Java + langchain4j）
  ├─ 通过 Feign 调用 del-product 的 /api/v1/search
  ├─ 直接调用 ES HTTP API 做 RAG
  └─ EmbeddingClient 可选择：
      ├─ 方案 A：保持 Java SDK
      └─ 方案 B：迁移到 Python（统一 AI 能力）
```

### 6.3 Phase 2 启动条件

- [ ] SEARCH-001 已完成并验收
- [ ] 菜数据已成功索引到 ES
- [ ] C 端搜索可用且性能达标
- [ ] del-order 新增退款接口（#REFUND-001，前置工单）

---

## 七、验收清单

### 7.1 功能验收

- [ ] 管理后台"重建索引"按钮可用
- [ ] ES 索引中有 600+ 个菜品
- [ ] C 端搜索 "番茄鸡蛋汤" 能匹配到相关菜品
- [ ] C 端搜索 "清淡" 能匹配到少油少盐的菜品
- [ ] C 端搜索 "潮汕" 能匹配到潮汕相关菜品
- [ ] 搜索响应时间 < 200ms（不含网络）

### 7.2 性能验收

- [ ] 单次搜索响应 < 200ms
- [ ] 全量重建 600 菜品 < 60 秒
- [ ] Wildcard 查询性能可接受（600 道菜规模）

### 7.3 安全验收

- [ ] ES 只内网暴露（不暴露 9200 端口到外网）
- [ ] DashScope API Key 通过环境变量注入
- [ ] 搜索 API 需要登录

### 7.4 扩展性验收

- [ ] `EmbeddingClient` 接口已抽取
- [ ] `ChineseTokenizer` 可被其他服务复用
- [ ] REST API 设计符合 LLM Tool 调用规范
- [ ] Nacos 配置分类清晰

---

## 八、风险评估

| 风险 | 等级 | 缓解 |
| |
|------|------|------|
| ES 宕机 | 🟡 中 | 降级到 MySQL |
| DashScope API 限额 | 🟡 中 | 批量调用，记录失败菜品 |
| Embedding 维度变更 | 🟢 低 | 配置化 |
| 知识库规则膨胀 | 🟢 低 | 600 道菜量级有限 |
| IK 分词器初始化慢 | 🟢 低 | 单例，启动后稳定 |
| Wildcard 查询性能 | 🟢 低 | 600 道菜量级没问题 |

---

## 九、时间线

| 天 | 任务 | 工程师 |
|----|------|--------|
| D1 | ES Docker + DashScope SDK 跑通 | 后端 + 运维 |
| D2 | ChineseTokenizer + EmbeddingClient + IndexingService | 后端 |
| D3 | QueryRouter / Parser / Expander | 后端 |
| D4 | SearchService + SearchController（Wildcard 查询）| 后端 |
| D5 | 管理后台"重建索引"按钮 | 前端 |
| D6 | C 端 SearchView | 前端 |
| D7 | 知识库 Nacos 配置 + 测试 | 后端 |
| D8 | 联调测试 + 修复 | 全员 |

---

## 十、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-05 |
| **方案变更**（IK → Java）| Codex | 2026-09-05 |
| 后端执行 | _待填_ | |
| 前端执行 | _待填_ | |
| 运维执行 | _待填_ | |
| 联调验收 | _待填_ | |

---

**完整 LLM 客服架构设计参见**：`docs/AI_CS_DESIGN.md

---

## 十一、实施经验教训（2026-09-05 实施过程)

SEARCH-001 实施过程暴露 5 个隐藏问题，全部通过运行时启动验证发现。

1. Spring Bean 命名冲突：两个 restTemplate() 同名，Spring Boot 3 禁止覆盖。修复：重命名为 dashScopeRestTemplate + @Qualifier
2. Spring Data ES Bean 名称约定：必须显式 @Bean("elasticsearchTemplate")
3. ES Analyzer 配置：未装的插件不能在 application.yml 写（ik_max_word 需要 IK 插件）
4. 类忘了 @Service 注解
5. @ConfigurationProperties 独立类需要 @EnableConfigurationProperties

### 经验总结
- 不在 IDEA 启动成功前声称完成
- Bean 命名要语义化，避免通用名
- 先跑通最小链路再扩展

---
`
**LLM 客服工单模板（待立项）**：`docs/WORK_ORDER_AI-CS-001.md`