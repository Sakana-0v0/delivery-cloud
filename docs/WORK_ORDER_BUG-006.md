# 📋 工单 #BUG-006：DashScopeEmbeddingClient 注入了 @LoadBalanced 的 RestTemplate

> **创建时间**：2026-09-06
> **优先级**：🔴 **P0**（语义搜索核心能力失效）
> **接收方**：后端工程师
> **预计工时**：30 分钟
> **依赖工单**：#BUG-004（已修复但只验证了兜底逻辑）

---

## 一、问题描述

### 1.1 现象

`DashScopeEmbeddingClient` 调用阿里云 Embedding API 时失败：

```
[DashScope] Embedding 批量失败: error=Service Instance cannot be null, serviceId: dashscope.aliyuncs.com
```

### 1.2 根本原因

**`del-common` 公共配置中定义了一个 @LoadBalanced 的 `restTemplate` Bean**：

```java
// E:\Idea_project\delivery-cloud\del-common\src\main\java\com\sakana\configs\RestTemplateConfig.java
@Bean
@LoadBalanced
public RestTemplate restTemplate() {        // ← 默认名"restTemplate"，被注入到了
    return new RestTemplate();
}
```

**当前 del-product 容器内 RestTemplate Bean 数量**：3 个
1. `restTemplate`（**@LoadBalanced**，从 del-common 继承）← **被注入了**
2. `externalRestTemplate`（普通，从 del-common 继承）
3. `dashScopeRestTemplate`（普通，del-product 自己定义）

`DashScopeEmbeddingClient` 虽然有 `@Qualifier("dashScopeRestTemplate")` 注解，但 Lombok 的 `@RequiredArgsConstructor` 可能没有正确传递 `@Qualifier`，导致**注入到了第一个（@LoadBalanced）Bean**。

### 1.3 后果

| 功能 | 状态 |
|------|------|
| 关键词搜索（番茄/汤/鸡蛋）| ✅ 正常（wildcard 兜底）|
| **Embedding 向量生成** | ❌ **完全失败** |
| **向量召回检索** | ❌ **不可用**（embedding 字段全 null）|

**虽然现在主要用 wildcard 搜索能用，但 SEARCH-001 的设计目标"语义搜索"实际并未实现。**

---

## 二、修复方案

### 方案 A（推荐）：改用 `externalRestTemplate`（del-common 已有的）

**改动 1**：`DashScopeEmbeddingClient.java`

```diff
  @Component
  @RequiredArgsConstructor
  public class DashScopeEmbeddingClient implements EmbeddingClient {
      
      private final DashScopeConfig dashScopeConfig;
-     @Qualifier("dashScopeRestTemplate")
+     @Qualifier("externalRestTemplate")
      private final RestTemplate restTemplate;
  }
```

**优点**：
- 改 1 行
- 复用 del-common 的外部 API Bean（语义清晰）
- 同时可以删除 del-product 里冗余的 `DashScopeRestConfig`

**改动 2**（可选清理）：删除 del-product 里的 `DashScopeRestConfig.java`

```bash
Remove-Item "E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\config\DashScopeRestConfig.java"
```

### 方案 B：保留 dashScopeRestTemplate 但修复 Lombok @Qualifier

**改动**：`DashScopeEmbeddingClient.java` 改用显式构造函数而非 @RequiredArgsConstructor

```java
@Component
public class DashScopeEmbeddingClient implements EmbeddingClient {
    
    private final DashScopeConfig dashScopeConfig;
    private final RestTemplate restTemplate;
    
    public DashScopeEmbeddingClient(
            DashScopeConfig dashScopeConfig,
            @Qualifier("dashScopeRestTemplate") RestTemplate restTemplate) {
        this.dashScopeConfig = dashScopeConfig;
        this.restTemplate = restTemplate;
    }
    // ...
}
```

**缺点**：需要写更多代码。

**方案 A 更优**：简洁 + 复用 del-common 已有 Bean + 顺便清理冗余。

---

## 三、推荐改动清单

| 文件 | 改动 | 行数 |
|------|------|------|
| `DashScopeEmbeddingClient.java` | `@Qualifier("dashScopeRestTemplate")` → `@Qualifier("externalRestTemplate")` | ±1 行 |
| `DashScopeRestConfig.java`（可选）| 删除整个文件 | -20 行 |
| `application.yml` | 无改动 | - |

---

## 四、验证清单

### 4.1 单元验证（看 Bean 注入正确性）

启动 del-product 后，应能在日志看到：
```
[索引重建] Embedding 成功生成: 50 个向量，维度: 1024
[索引重建] 写入 ES 成功: 50 条
```

而不是：
```
[DashScope] Embedding 批量失败: error=Service Instance cannot be null
[索引重建] Embedding 失败，跳过向量
```

### 4.2 端到端验证

```bash
# 1. 触发同步重建
curl -X POST "http://localhost:10010/api/v1/admin/index/rebuild?async=false" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
# 预期：{"success": 737, "failed": 0, "costMs": 60000+}

# 2. 验证 ES 文档包含 embedding
curl -s "http://localhost:9200/dish/_search?size=1&pretty" 2>&1 | grep -A 1 "embedding"
# 预期：看到非空的 embedding 数组（1024 个 float）
# 修复前：embedding: null

# 3. 验证 embedding 长度
curl -s "http://localhost:9200/dish/_doc/1" 2>&1 | python -c "
import json, sys
data = json.loads(sys.stdin.read())
emb = data.get('_source', {}).get('embedding', [])
print(f'Embedding length: {len(emb)}')
print(f'First 5 values: {emb[:5] if emb else \"null\"}')"
# 预期：Embedding length: 1024
# 预期：First 5 values: [some numbers]
```

### 4.3 语义搜索验证（未来能力）

虽然向量搜索还未在 SearchService 实现，但可以验证 embedding 字段存在：
```bash
# 测一下 ES 能不能用 embedding 做向量查询
curl -X POST "http://localhost:9200/dish/_search" -H "Content-Type: application/json" -d '{
  "knn": {
    "field": "embedding",
    "query_vector": [0.1, 0.2, ... 1024 个值],
    "k": 5
  }
}'
# 预期：返回 top-5 相似菜品
```

---

## 五、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 修改后引入新问题 | 🟢 低 | 改动小，影响范围明确 |
| 索引重建耗时 | 🟡 中 | embedding API 调用慢（600 道菜 10-30 秒） |
| API Key 失效 | 🟢 低 | 单独 issue，与本工单无关 |

**注意**：索引重建时会调用 DashScope API，**会消耗 API 配额**。600 道菜 × 1024 维 × DashScope v3 价格约 ¥0.0007/1k tokens → 总成本约 ¥0.4。

---

## 六、对当前已修复功能的影响

| 工单 | 状态 | 影响 |
|------|------|------|
| #BUG-004 核心搜索 | ✅ 验收通过 | 不受影响（修复的是 wildcard 路径）|
| #BUG-005 知识库扩展 | ⏳ 待修 | 不受影响 |
| **#BUG-006 Embedding** | 🔴 **当前** | 修复后才能完整支持语义搜索 |

**注意**：即使 #BUG-004 验收通过，**核心设计目标"语义搜索"实际只实现了一半**（关键词搜索通，向量搜索没通）。这不算 #BUG-004 验收失败，但需要 #BUG-006 修复才能算完整。

---

## 七、相关工单

| 工单 | 状态 |
|------|------|
| #SEARCH-001 | ⏳ 暂不关闭（待 BUG-006 修复）|
| #BUG-004 | ✅ 已关闭（核心搜索可用）|
| #BUG-005 | ⏳ 待修（知识库扩展）|
| **#BUG-006**（本工单）| 🔴 待执行 |

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 根因定位 | Codex | 2026-09-06 |
| 修复 | _待后端工程师_ | |
| 验收 | _待填_ | |

---

**修复命令**（最快路径）：
1. 改 `DashScopeEmbeddingClient.java` line 41 的 `@Qualifier` 值
2. （可选）删除 `DashScopeRestConfig.java`
3. 重启 del-product
4. 跑验证清单