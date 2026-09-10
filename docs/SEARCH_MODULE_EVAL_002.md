# 📋 智能菜品搜索模块架构评估报告 v2

> **评估日期**：2026-09-05
> **版本**：v2（基于真实数据：600+ 道菜 + qwen embedding）
> **对比**：v1 基于"18 道菜"的过时假设已重评

---

## 一、关键事实更新

| 维度 | v1 评估 | v2 修正 |
|------|--------|---------|
| 菜品数据量 | 18 道菜 | **600+ 道菜** |
| 业务需求 | 不确定 | **明确需要 ES** |
| Embedding | 未设计 | **qwen3.7-text-embedding (DashScope)** |
| ES 必要性 | 可选 | **必备** |
| 原设计"过度工程化"判断 | 成立 | ❌ 不成立 |

---

## 二、最终决策（已与用户确认）

| 决策点 | 选择 | 理由 |
|--------|------|------|
| **Embedding 服务** | ✅ **DashScope Java SDK 直接调用** | 不加新服务 |
| **embedding 生成时机** | ✅ **管理后台"重建索引"按钮** | 600 条可控 |
| **ES 部署** | ✅ **Docker 单节点** | 600 条够用 |
| **服务归属** | ✅ **全部在 del-product** | 不加第 10 个服务 |
| **知识库存储** | ✅ **Nacos 配置中心** | 不写文件监听 |
| **数据同步** | ✅ **不用 Canal** | 600 条人工触发够 |

---

## 三、修订后架构图

```
┌────────────────────────────────────────────────────┐
│       前端 (Vue 3 + Element Plus)                   │
│       SearchBox.vue / SearchResult.vue              │
└────────────────────┬───────────────────────────────┘
                     │ /api/v1/search?query=...
                     ▼
┌────────────────────────────────────────────────────┐
│       del-product (Java Spring Boot)                │
│                                                    │
│  ┌──────────────┐ ┌──────────────────────────┐ │
│  │SearchController   │ SearchServiceImpl │ │
│  └──────────────┘    │  ├─ QueryRouter           │ │
│                      │  ├─ QueryParser           │ │
│                      │  ├─ QueryExpander (KB)    │ │
│                      │  ├─ ES 检索              │ │
│                      │  └─ MySQL 精排            │ │
│                      └──────────────────────────┘ │
│                                                    │
│  ┌──────────────┐    ┌──────────────────────────┐ │
│  │AdminIndexController│ EmbeddingClient (DashScope SDK)│
│  └──────────────┘    │   text-embedding-v3     │ │
│         │            └──────────────────────────┘ │
│         ▼                       │                  │
│  ┌──────────────┐               │                  │
│  │IndexingService│◀──────────────┘                  │
│  └──────────────┘                                  │
└──────────┬─────────────────┬───────────────────────┘
           │                 │
           ▼                 ▼
    ┌─────────┐       ┌─────────────┐
    │ ES 8.x  │       │  DashScope  │
    │ (单节点) │       │  (云端 API)  │
    └─────────┘       └─────────────┘

知识库：存 Nacos 配置中心（不写文件监听）
```

---

## 四、与项目现状对接

| 设计要素 | 项目现状 | 对接方式 |
|---------|---------|---------|
| SearchController | del-product 已有 ProductController | 在 del-product 新增 |
| Query Understanding | 无 | del-product 新建 Service |
| Knowledge Base | 无 | **用 Nacos 配置中心** |
| Elasticsearch | 无 | **新增 ES 8.x 单节点容器** |
| Embedding | 无 | **DashScope SDK**（不部署服务） |
| MySQL 精排 | del-product 已有 | 复用 |
| Caffeine L1 | del-product 已有 | 复用 |
| Redis L2 | del-product 已有 | 复用 |

---

## 五、工作量评估

| 模块 | 工期 |
|------|------|
| ES Docker 部署 | 1 天 |
| DashScope Embedding 接入 | 1 天 |
| 菜品数据导入 + 索引 | 0.5 天 |
| 知识库配置（Nacos）| 1 天 |
| Search Service 实现 | 2 天 |
| 前端搜索 UI | 1 天 |
| 管理后台"重建索引" | 0.5 天 |
| 联调测试 | 1 天 |
| **总计** | **8 天** |

---

## 六、报告交付

最终方案见 `docs/WORK_ORDER_SEARCH-001.md`