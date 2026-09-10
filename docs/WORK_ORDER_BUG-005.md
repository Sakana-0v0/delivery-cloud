# 📋 工单 #BUG-005：知识库扩展配置 prefix 不匹配导致扩展不生效

> **创建时间**：2026-09-06
> **优先级**：P2（核心搜索可用，仅扩展功能失效）
> **接收方**：后端工程师
> **预计工时**：10 分钟
> **依赖工单**：#SEARCH-001（已通过验收）

---

## 一、问题确认

### 1.1 现象

`KnowledgeBaseService` 的 prefix 与 yml 配置格式不匹配：

| 位置 | prefix / 配置 | 格式 |
|------|------------|------|
| `KnowledgeBaseService.java` line 23 | `@ConfigurationProperties(prefix = "search.expansion-rules")` | **横线** |
| `application.yml` line 71 | `search.expansion.rules` | **点** |

Spring Boot 严格匹配 prefix → 绑定失败 → `expansionRules.rules` 永远是 null → 知识库扩展始终为空。

### 1.2 测试证据

```
搜 "番茄" → 9 条结果（直接匹配 nameTokens）✅
搜 "清淡" → 0 条结果（应该扩展为"少油/少盐/素"，但没生效）❌
```

### 1.3 影响范围

| 功能 | 状态 |
|------|------|
| 关键词直接搜索（番茄/汤/鸡蛋）| ✅ 正常 |
| 知识库扩展（清淡→少油）| ❌ 不生效 |
| 结构化过滤（蛋白质>20）| ⚠️ 待验证（可能被波及）|

---

## 二、修复方案

**改 prefix**（推荐）：把横线改为点，匹配 yml。

### 改动 1：KnowledgeBaseService.java

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\service\KnowledgeBaseService.java`

```diff
  @Data
- @ConfigurationProperties(prefix = "search.expansion-rules")
+ @ConfigurationProperties(prefix = "search.expansion")
  public static class ExpansionRules {
      private Map<String, RuleEntry> rules;
  }
```

### 改动 2：同步更新 KnowledgeConfig.java 注释

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\search\config\KnowledgeConfig.java`

注释里写的是错的示例，修正：

```diff
- * search:
- *   expansion-rules:
- *     rules:
+ * search:
+ *   expansion:
+ *     rules:
  *       清淡:
  *         terms: [少油, 少盐, 清淡]
  *         minProtein: 5
```

### 改动 3：application.yml 确认配置

**文件**：`E:\Idea_project\delivery-cloud\del-product\src\main\resources\application.yml`

当前配置（已对）：

```yaml
search:
  expansion:
    rules:
      清淡:
        - 少油
        - 少盐
        - 清淡
        - 素
      高蛋白:
        - 蛋白质
        - 健身
        - 鸡胸肉
        - 牛肉
```

✅ yml 已经用点格式，**改完 prefix 即可生效**。

---

## 三、为什么没早点发现

| 检查项 | 结果 |
|--------|------|
| 编译通过 | ✅ |
| 服务启动 | ✅ |
| 接口返回 200 | ✅ |
| 知识库扩展生效 | ❌ 一直无效 |

**原因**：
- 前端测试用"番茄"等直接命中的词，没用"清淡"等需要扩展的词
- 单元测试可能没覆盖 KnowledgeBaseService 路径
- 异常没有明显的日志暴露

**教训**：搜索功能的验收必须测**多类型查询词**（直接词、扩展词、结构化词）。

---

## 四、验收清单

```bash
# 1. 重启 del-product
#    IDEA 重启服务

# 2. 直接词搜索
curl "http://localhost:10010/api/v1/search?query=%E7%95%AA%E8%8C%84" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：返回含番茄的菜品

# 3. 扩展词搜索（修复后应该返回结果）
curl "http://localhost:10010/api/v1/search?query=%E6%B8%85%E6%B7%A1" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：返回含"少油/少盐/清淡/素"的菜品
# 修复前：0 条
# 修复后：应 > 0 条

# 4. 高蛋白扩展
curl "http://localhost:10010/api/v1/search?query=%E9%AB%98%E8%9B%8B%E7%99%BD" \
  -H "Authorization: Bearer ${USER_TOKEN}"
# 预期：返回鸡胸肉/牛肉等高蛋白商品

# 5. 验证 Nacos 配置生效（确认 prefix 对了）
# 在 Nacos 控制台改 search.expansion.rules 加一条规则
# 几秒后（热更新）搜索应该能命中新规则
```

---

## 五、为什么 P2 而不是 P0

| 维度 | 评估 |
|------|------|
| 核心搜索 | ✅ 可用（直接词搜索全部正常）|
| 用户体验 | ⚠️ 部分功能受限（语义扩展失效）|
| 数据准确性 | ✅ 不影响（数据本身正确）|
| 业务可用性 | ✅ 短期可接受 |

**结论**：可推迟到 #SEARCH-001 整体验收前修复，不阻塞。

---

## 六、相关建议

### 6.1 加单元测试

为 `KnowledgeBaseService` 加测试：

```java
@SpringBootTest
public class KnowledgeBaseServiceTest {
    @Autowired KnowledgeBaseService kbService;
    
    @Test
    public void testExpandTerms() {
        List<String> terms = kbService.getExpandedTerms("清淡");
        assertTrue(terms.contains("少油"));
        assertTrue(terms.contains("少盐"));
    }
}
```

### 6.2 启动时校验

加 `@PostConstruct` 启动检查：

```java
@PostConstruct
public void checkRulesLoaded() {
    if (expansionRules == null || expansionRules.getRules() == null) {
        log.error("[知识库] 规则未加载！请检查配置 search.expansion.rules");
    } else {
        log.info("[知识库] 加载 {} 条规则", expansionRules.getRules().size());
    }
}
```

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 根因定位 | Codex | 2026-09-06 |
| 修复 | _待后端工程师_ | |
| 验收 | _待填_ | |

---

**下一步**：
1. 改 prefix（1 行代码）
2. 重启 del-product
3. 跑验证清单
4. #SEARCH-001 整体可关闭