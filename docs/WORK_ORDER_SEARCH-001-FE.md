# 📋 工单 #SEARCH-001-FE：搜索功能前端实现 + 联调

> **创建时间**：2026-09-06
> **优先级**：🟡 **P1**（后端已就绪，前端阻塞用户使用）
> **接收方**：前端工程师
> **预计工时**：1.5 天（前端 1 天 + 联调 0.5 天）
> **依赖工单**：✅ #SEARCH-001 后端已通过验收
> **后端接口**：
> - C 端：`GET /api/v1/search?query=&page=&size=`
> - B 端：`POST /api/v1/admin/index/rebuild?async=true|false`

---

## 一、当前真实状态

| 项 | 状态 |
|------|------|
| 后端 API | ✅ 可用 |
| ES 索引 | ✅ 737 条数据 |
| **前端入口** | ❌ **不存在** |
| **用户能用吗？**| ❌ **不能** |

后端接口已通，**用户却用不到**，这是典型的"半成品"状态。

---

## 二、需新增/修改的文件

### 2.1 新增文件（3 个）

| 文件 | 类型 | 说明 |
|------|------|------|
| `src/api/search.ts` | 新建 | 搜索相关 API 封装 |
| `src/views/SearchView.vue` | 新建 | C 端搜索结果页 |
| `src/views/admin/AdminIndexView.vue` | 新建 | B 端"重建索引"页面 |

### 2.2 修改文件（2 个）

| 文件 | 改动 | 说明 |
|------|------|------|
| `src/router/index.ts` | 加 2 个路由 | /search 和 /admin/index |
| `src/views/admin/AdminLayout.vue` | 侧边栏加菜单 | "数据索引" 入口 |

---

## 三、详细实现

### 3.1 `src/api/search.ts`（新建）

```typescript
import request from './request'

/** 搜索请求 */
export interface SearchRequest {
  query: string
  page?: number
  size?: number
}

/** 搜索结果项 */
export interface SearchProduct {
  id: number
  fid: string
  name: string
  description: string
  category: string
  cover: string
  price: number
  calories?: number
  protein?: number
  fat?: number
  available: boolean
}

/** 搜索响应 */
export interface SearchResponse {
  products: SearchProduct[]
  total: number
  page: number
  size: number
  costMs: number
}

/** C 端搜索 */
export function searchProducts(params: SearchRequest) {
  return request.get('/search', { params }) as Promise<SearchResponse>
}

/** B 端重建索引 */
export interface RebuildResult {
  status: 'started' | 'completed'
  message: string
  success?: number
  failed?: number
  total?: number
  costMs?: number
}

export function rebuildIndex(async = true) {
  return request.post(`/admin/index/rebuild?async=${async}`) as Promise<RebuildResult>
}

/** B 端查询索引状态 */
export function getIndexStatus() {
  return request.get('/admin/index/status')
}
```

### 3.2 `src/views/SearchView.vue`（新建）

```vue
<template>
  <div class="search-page">
    <!-- 顶部搜索框 -->
    <div class="search-box">
      <el-input
        v-model="query"
        placeholder="搜索菜品（试试：番茄、清淡、高蛋白）"
        size="large"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch" :icon="Search" />
        </template>
      </el-input>
    </div>

    <!-- 结果统计 -->
    <div v-if="results" class="result-meta">
      找到 <strong>{{ results.total }}</strong> 个结果
      <span v-if="results.costMs">（{{ results.costMs }}ms）</span>
    </div>

    <!-- 结果列表 -->
    <div v-if="results?.products?.length" class="result-list">
      <div
        v-for="p in results.products"
        :key="p.id"
        class="result-item"
        @click="goDetail(p.fid)"
      >
        <img :src="p.cover" class="cover" />
        <div class="info">
          <h3>{{ p.name }}</h3>
          <p class="category">{{ p.category }}</p>
          <p class="description">{{ p.description }}</p>
          <div class="meta">
            <span class="price">¥{{ p.price }}</span>
            <span v-if="p.calories">{{ p.calories }} 千卡</span>
            <span v-if="p.protein">蛋白 {{ p.protein }}g</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空结果 -->
    <el-empty v-else-if="results" description="没有找到匹配的菜品" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { searchProducts, type SearchResponse } from '@/api/search'

const route = useRoute()
const router = useRouter()
const query = ref(route.query.q as string || '')
const results = ref<SearchResponse | null>(null)
const loading = ref(false)

async function handleSearch() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    results.value = await searchProducts({
      query: query.value,
      page: 1,
      size: 20
    })
    // 同步 URL
    router.replace({ query: { q: query.value } })
  } finally {
    loading.value = false
  }
}

function goDetail(fid: string) {
  router.push(`/product/${fid}`)
}

if (query.value) handleSearch()
</script>

<style scoped>
.search-page { padding: 20px; }
.search-box { max-width: 600px; margin: 0 auto 20px; }
.result-meta { color: #999; margin-bottom: 12px; }
.result-list { display: grid; gap: 12px; }
.result-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
}
.result-item:hover { background: #fafafa; }
.cover { width: 80px; height: 80px; object-fit: cover; border-radius: 4px; }
.info h3 { margin: 0 0 4px 0; font-size: 16px; }
.category { color: #999; font-size: 12px; margin: 0 0 4px 0; }
.description { color: #666; font-size: 13px; margin: 0 0 8px 0; }
.meta { display: flex; gap: 12px; font-size: 13px; }
.price { color: #ff7a00; font-weight: bold; }
</style>
```

### 3.3 `src/views/admin/AdminIndexView.vue`（新建）

```vue
<template>
  <div class="admin-index">
    <h2>菜品搜索索引管理</h2>

    <el-alert
      title="ES 索引重建"
      type="info"
      :closable="false"
      show-icon
    >
      重建索引会从 t_product 表全量扫描并写入 ES。
      异步模式立即返回（推荐），同步模式等待完成后返回。
    </el-alert>

    <div class="actions">
      <el-button
        type="primary"
        :loading="loading"
        @click="handleRebuild(true)"
      >
        异步重建（推荐）
      </el-button>
      <el-button
        :loading="syncLoading"
        @click="handleRebuild(false)"
      >
        同步重建
      </el-button>
    </div>

    <!-- 最近任务结果 -->
    <el-card v-if="lastTask" class="result-card">
      <template #header>
        <span>最近重建任务</span>
        <el-tag :type="lastTask.status === 'completed' ? 'success' : 'info'">
          {{ lastTask.status }}
        </el-tag>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="状态">{{ lastTask.status }}</el-descriptions-item>
        <el-descriptions-item label="消息">{{ lastTask.message }}</el-descriptions-item>
        <el-descriptions-item v-if="lastTask.success" label="成功数">{{ lastTask.success }}</el-descriptions-item>
        <el-descriptions-item v-if="lastTask.failed" label="失败数">{{ lastTask.failed }}</el-descriptions-item>
        <el-descriptions-item v-if="lastTask.costMs" label="耗时">{{ lastTask.costMs }}ms</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- ES 状态 -->
    <el-card class="status-card">
      <template #header>ES 索引当前状态</template>
      <el-button @click="checkESStatus" :loading="checking">查看 ES 文档数</el-button>
      <pre v-if="esStatus" class="es-output">{{ esStatus }}</pre>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { rebuildIndex, getIndexStatus } from '@/api/search'

const loading = ref(false)
const syncLoading = ref(false)
const lastTask = ref<any>(null)
const checking = ref(false)
const esStatus = ref('')

async function handleRebuild(async: boolean) {
  const fn = async ? (loading.value = true) : (syncLoading.value = true)
  try {
    const result = await rebuildIndex(async)
    lastTask.value = result
    if (result.status === 'started') {
      ElMessage.success('异步重建已启动，30 秒后刷新查看结果')
      setTimeout(() => checkESStatus(), 5000)
    } else {
      ElMessage.success(`重建完成：成功 ${result.success}，失败 ${result.failed}，耗时 ${result.costMs}ms`)
    }
  } finally {
    fn(false)
  }
}

async function checkESStatus() {
  checking.value = true
  try {
    const resp: any = await getIndexStatus()
    esStatus.value = JSON.stringify(resp, null, 2)
  } finally {
    checking.value = false
  }
}
</script>

<style scoped>
.admin-index { padding: 20px; max-width: 800px; }
.actions { margin: 20px 0; display: flex; gap: 12px; }
.result-card, .status-card { margin-top: 20px; }
.es-output { background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px; }
</style>
```

### 3.4 `src/router/index.ts` 修改

```typescript
// 在 routes 数组中添加：
{
  path: '/search',
  name: 'Search',
  component: () => import('@/views/SearchView.vue'),
  meta: { title: '搜索菜品' }
},
{
  path: '/admin/index',
  name: 'AdminIndex',
  component: () => import('@/views/admin/AdminIndexView.vue'),
  meta: { title: '索引管理', requiresAdmin: true }
}
```

### 3.5 `src/views/admin/AdminLayout.vue` 修改

在侧边栏菜单数组中加一项：

```typescript
{
  path: '/admin/index',
  icon: 'Database',
  title: '数据索引'
}
```

---

## 四、联调测试清单

### 4.1 C 端搜索联调

```bash
# 1. 启动前端 dev server
cd E:\VSCode_workspace\Delivery
npm run dev

# 2. 浏览器打开 http://localhost:5173/search?q=番茄
# 预期：看到搜索结果列表
# 预期：URL 同步 query 参数
# 预期：响应时间 < 1s

# 3. 测试不同查询
?q=清淡    → 5 条
?q=阿根廷  → 15 条
?q=高蛋白  → 4 条
?q=xyz不存在 → 0 条 + 空状态
```

### 4.2 B 端索引管理联调

```bash
# 1. 浏览器打开 http://localhost:5173/admin/index（用 superadmin 登录）
# 预期：看到"异步重建/同步重建"两个按钮

# 2. 点"异步重建"
# 预期：提示"异步重建已启动"

# 3. 等 30 秒后点"查看 ES 文档数"
# 预期：输出 {"code":0,"data":{"indexName":"dish_index","status":"active",...}}

# 4. 点"同步重建"
# 预期：等待 30 秒后显示"成功 737，失败 0，耗时 30xxx ms"
```

### 4.3 端到端测试

```
完整流程：
1. 打开 /search?q=番茄 → 看到 105 条结果
2. 清空 ES 索引（后端代码 delete dish index）
3. 打开 /admin/index → 点"同步重建"
4. 等完成后 → 打开 /search?q=番茄 → 看到结果回来了
```

---

## 五、验收清单

| # | 项 | 验证 |
|---|------|------|
| 1 | `SearchView.vue` 显示搜索框 + 结果列表 | |
| 2 | URL 同步 query 参数 | |
| 3 | "清淡" 命中 5 条（知识库扩展）| |
| 4 | "番茄" 命中 105 条（多字段召回）| |
| 5 | "xyz不存在" 显示空状态 | |
| 6 | `AdminIndexView.vue` 两个按钮可用 | |
| 7 | 同步重建返回 success/failed | |
| 8 | ES 状态查询正常 | |
| 9 | AdminLayout 侧边栏有"数据索引"入口 | |
| 10 | 端到端：删索引 → 重建 → 搜索恢复 | |

---

## 六、工作量估算

| 文件 | 工作量 |
|------|--------|
| `src/api/search.ts` | 0.5 小时 |
| `src/views/SearchView.vue` | 2 小时 |
| `src/views/admin/AdminIndexView.vue` | 2 小时 |
| `src/router/index.ts` | 0.5 小时 |
| `src/views/admin/AdminLayout.vue` | 0.5 小时 |
| 联调测试 | 2 小时 |
| **总计** | **7 小时** |

---

## 七、风险与备注

| 项 | 说明 |
|------|------|
| 接口路径 | 前端调 `/search`（不带 `/api/v1`，由 del-gateway 路由）|
| Token | 复用现有 request.ts（自动带 USER/ADMIN token）|
| ADMIN token | `rebuildIndex` 接口需要 SUPER_ADMIN，tokenType 需正确 |
| 错误处理 | 接口失败要显示 ElMessage.error |

---

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-06 |
| 前端执行 | _待前端工程师_ | |
| 联调 | _待后端+前端联合_ | |
| 验收 | _待填_ | |

---

**重要**：执行完毕后，#SEARCH-001 才能算**端到端完成**。当前仅"后端完成"。