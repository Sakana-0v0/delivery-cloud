# 📋 工单 #SEARCH-001-VO：后端新增 SearchProductVO + 前端同步

> **创建时间**：2026-09-06
> **优先级**：🔴 P0（数据严重失真）
> **接收方**：后端 + 前端联合实施
> **预计工时**：后端 30 分钟 + 前端 15 分钟 = **45 分钟**
> **依赖工单**：✅ #SEARCH-001 + #SEARCH-001-FE

---

## 一、问题确认（已亲自验证）

### 1.1 字段不对齐

| 前端 `SearchProduct` | 后端 `Product` | 不匹配 |
|----------------------|----------------|--------|
| `id: number` | `fid: String` | 命名 + 类型都不对 |
| `category: string` | `categoryId: Long` | 语义错 |
| `price: number` | `realPrice: BigDecimal` | 字段名错 |
| `calories/protein/fat` | 完全无此字段 | 数据缺失 |
| `available: boolean` | `status: Integer (0/1)` | 类型错 |

### 1.2 影响

- 分类标签显示 "1"、"2" 这种数字（不是名字）
- 热量/蛋白/脂肪徽章永远不显示
- 价格可能正常（前端代码已临时兜底）
- 页面不崩溃但数据严重错

---

## 二、修复方案

后端引入 `SearchProductVO` 展示层 DTO，合并 MySQL Product + ES DishDocument，前端按 DTO 契约使用。

---

## 三、后端改动

### 3.1 新增文件

`del-product/src/main/java/com/sakana/search/dto/SearchProductVO.java`

```java
package com.sakana.search.dto;

import lombok.Builder;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
public class SearchProductVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private String cover;
    private String category;
    private BigDecimal price;
    private Integer calories;
    private Float protein;
    private Float fat;
    private Boolean available;
}
```

### 3.2 修改 SearchResponse.java

```diff
- private List<Product> products;
+ private List<SearchProductVO> products;
```

### 3.3 修改 SearchServiceImpl.java

**注入 CategoryMapper**：

```java
private final CategoryMapper categoryMapper;
```

**search() 方法返回前替换为 DTO 转换**：

```java
// 1. DishDocument 按 fid 建索引
Map<String, DishDocument> docMap = hits.stream()
        .collect(Collectors.toMap(
                h -> String.valueOf(h.getContent().getId()),
                h -> h.getContent(),
                (a, b) -> a));

// 2. 批量查 Category（避免 N+1）
List<Long> categoryIds = orderedProducts.stream()
        .map(Product::getCategoryId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
Map<Long, String> categoryMap = categoryIds.isEmpty()
        ? Collections.emptyMap()
        : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(
                        Category::getId, Category::getName, (a, b) -> a));

// 3. Product → SearchProductVO
List<SearchProductVO> vos = orderedProducts.stream().map(p -> {
    DishDocument doc = docMap.get(p.getFid());
    BigDecimal price = p.getRealPrice() != null ? p.getRealPrice() : p.getNormPrice();
    return SearchProductVO.builder()
            .id(p.getFid())
            .name(p.getName())
            .description(p.getDescription())
            .cover(p.getCover())
            .category(categoryMap.getOrDefault(p.getCategoryId(), ""))
            .price(price)
            .calories(doc != null ? doc.getCalories() : null)
            .protein(doc != null ? doc.getProtein() : null)
            .fat(doc != null ? doc.getFat() : null)
            .available(p.getStatus() != null && p.getStatus() == 0)
            .build();
}).collect(Collectors.toList());

return SearchResponse.builder()
        .products(vos)
        .total(hits.getTotalHits())
        ...
        .build();
```

**searchFallback() 同样转换**（用空 category、null 营养字段）。

**新增 imports**：
```java
import com.sakana.dao.entity.Category;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.search.document.DishDocument;
import com.sakana.search.dto.SearchProductVO;
import java.math.BigDecimal;
import java.util.Objects;
```

---

## 四、前端改动

### 4.1 修改 src/api/search.ts

```diff
  export interface SearchProduct {
-     id: number
+     id: string
      fid: string
      ...
  }
```

### 4.2 修改 SearchView.vue

```diff
- <span class="price">¥{{ (p.realPrice ?? p.normPrice ?? p.price)?.toFixed(2) ?? '—' }}</span>
+ <span class="price">¥{{ p.price?.toFixed(2) ?? '—' }}</span>

- <div v-for="p in results.products" :key="p.id" class="result-item">
+ <div v-for="p in results.products" :key="p.fid" class="result-item">
```

---

## 五、验证清单

```bash
# 后端
mvn clean compile -pl del-product -am -DskipTests
# 重启 del-product

curl "http://localhost:10010/api/v1/search?query=番茄" \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool

# 预期：
# "category": "汤品"     ← 名字
# "price": 18,           ← number
# "calories": 200        ← ES 字段
```

```bash
# 前端
cd E:\VSCode_workspace\Delivery
npm run dev
# 浏览器测试 /search?q=番茄
# - 分类显示名字（不是数字 1）
# - 营养徽章显示
```

---

## 六、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-06 |
| 后端实施 | _待后端_ | |
| 前端实施 | _待前端_ | |
| 联调验收 | _待填_ | |