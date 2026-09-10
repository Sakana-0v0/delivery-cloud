# 📋 工单 #BE-GW-001：del-product 安全配置不识别 admin JWT

> **创建时间**：2026-09-04
> **问题来源**：B 端管理后台评价开关页面报 401 Unauthorized
> **修复方案**：方案 A（不改变架构，修复安全配置）
> **接收方**：后端工程师 / 运维工程师

---

## 一、问题描述

### 1.1 错误现象

管理员登录 B 端后，访问"评价管理"页面（`/admin/reviews`）时：

```
GET /api/v1/admin/reviews/switch 401 (Unauthorized)
```

接口返回 401，前端弹出"登录已过期，请重新登录"。

### 1.2 影响范围

| 功能 | 接口 | 状态 |
|------|------|------|
| 评价开关查询 | `GET /api/v1/admin/reviews/switch` | ❌ 401 |
| 👍 开关设置 | `PUT /api/v1/admin/reviews/praise` | ❌ 401 |
| 👎 开关设置 | `PUT /api/v1/admin/reviews/bad` | ❌ 401 |

---

## 二、根因分析（方案 A）

### 2.1 架构现状

```
请求链路：
前端 → 网关 → del-product → AdminReviewController（存在）
                    ↑
              del-product 的 SecurityConfig
              只认 USER_POOL JWT，不认 ADMIN_POOL JWT
```

### 2.2 问题定位

`del-product` 中的 `AdminReviewController` **已存在**（路径：`/api/v1/admin/reviews/**`），但 `del-product` 的安全配置**只放行了 USER_POOL 的 JWT**，不识别 ADMIN_POOL 的 JWT。

**证据**：`del-product` 路由在网关配置中未被移除，说明架构意图是让 admin/reviews 留在 `del-product`。

### 2.3 解决方案（方案 A）

在 `del-product` 的安全配置中，将 `/api/v1/admin/reviews/**` 路径加入白名单，允许 ADMIN_POOL JWT 通过。

---

## 三、修复方案

### 3.1 修改文件

**文件路径**：`E:\Idea_project\delivery-cloud\del-product\src\main\java\com\sakana\web\config\SecurityConfig.java`

（文件名可能为 `SecurityConfig`、`WebSecurityConfig` 或类似，请搜索 `del-product` 中的 `*Security*` 文件）

### 3.2 修复逻辑

在 `del-product` 的安全配置中，找到现有的路径白名单配置，新增：

```java
// /api/v1/admin/reviews/** 路径允许 ADMIN JWT 通过
```

**具体做法**：

1. 找到 `SecurityConfig` 中配置白名单路径的位置
2. 确认是否已有 `/api/v1/admin/**` 或类似路径在白名单中
3. 如果没有，添加 `/api/v1/admin/reviews/**` 到白名单
4. 确认白名单路径使用 ADMIN_POOL 的 JWT 验证方式

### 3.3 预期结果

```yaml
# 安全配置修复后
/api/v1/admin/reviews/** → 允许 ADMIN JWT → AdminReviewController → 正常返回 200
```

---

## 四、验证清单

- [ ] 找到 `del-product` 的安全配置文件
- [ ] 确认 `/api/v1/admin/reviews/**` 路径已加入白名单
- [ ] 编译 `del-product` 服务：`mvn clean compile -pl del-product -am -DskipTests -s settings.xml`
- [ ] 重启 `del-product` 服务
- [ ] B 端管理员重新登录（获取新 token）
- [ ] 访问 `/admin/reviews` 页面，确认接口返回 200
- [ ] 点击 👍/👎 开关，确认接口返回 200

---

## 五、联调说明

**本工单需与 `FE-AUTH-001`（前端 Token 指定）配合使用**：

| 工单 | 负责方 | 作用 |
|------|--------|------|
| `FE-AUTH-001` | 前端 | 请求中携带 ADMIN_POOL token |
| `BE-GW-001` | 后端 | del-product 识别并放行 ADMIN JWT |

**两个工单必须同时执行，否则问题无法完全解决。**

---

## 六、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 安全配置被覆盖 | 低 | 仅增加路径白名单，不改动其他配置 |
| 影响 C 端功能 | 无 | 仅涉及 admin 路径 |
| 路径匹配范围过宽 | 中 | 确保只放行 `/api/v1/admin/reviews/**` |

---

**后端工程师 / 运维工程师执行完成后告知我结果。**
