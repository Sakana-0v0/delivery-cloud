# 前后端接口字段对齐审计报告（最终版）

> **审计日期**：2026-09-03
> **审计范围**：C 端用户侧 + B 端管理后台全部接口
> **前端工作区**：E:\VSCode_workspace\Delivery
> **后端代码**：E:\Idea_project\delivery-cloud
> **报告状态**：核实完成

---

## 执行摘要

| 类别 | 数量 | 说明 |
|------|------|------|
| FAIL 严重问题 | 5 个 | 字段名不匹配，会导致页面数据无法显示 |
| WARN 中等问题 | 3 个 | 精度丢失或待确认 |
| OK 完全对齐 | 大部分接口 | 字段名和类型完全一致 |

**结论**：B 端订单列表/详情页面存在 4 个字段名不匹配（会导致状态显示为空、收货地址为空、完成时间不显示），需立即修复；C 端整体对齐情况良好。

---

## 一、P0 严重问题（必须修复）

### P0-1 ~ P0-4：B 端订单字段名不匹配

**问题位置**：src/api/admin/order.ts（前端）vs del-order/.../web/vo/AdminOrderVO.java（后端）

### 字段对照表

| 编号 | 后端真实字段名 | 前端使用字段名 | 不匹配影响 | 修复方 |
|------|--------------|-------------|-----------|--------|
| P0-1 | statusDesc | statusText | 订单状态文字显示为空 | 前端 |
| P0-2 | receiverAddress | addressDetail | 收货地址显示为空 | 前端 |
| P0-3 | completeTime | finishTime | 完成时间显示为空 | 前端 |
| P0-4 | items[].productCover | items[].cover | 商品图片不显示 | 前端 |

### 后端源码（已核实）

文件：del-order/src/main/java/com/sakana/web/vo/AdminOrderVO.java

```
@Data
public class AdminOrderVO {
    private Integer status;
    private String statusDesc;           // 后端真实字段
    private String receiverAddress;     // 后端真实字段
    private LocalDateTime completeTime;  // 后端真实字段
    private List<OrderItemVO> items;
}
```

文件：del-order/src/main/java/com/sakana/web/vo/OrderItemVO.java

```
public class OrderItemVO {
    private String productCover;  // 后端真实字段
}
```

### 前端源码（已核实）

文件：src/api/admin/order.ts

```
export interface AdminOrder {
  statusText?: string           // 前端错误字段名
  addressDetail?: string        // 前端错误字段名
  finishTime?: string           // 前端错误字段名
}

export interface AdminOrderItem {
  cover?: string                // 前端错误字段名
}
```

文件：src/views/admin/OrderManageView.vue（模板使用处）

```
<span>{{ detailData.addressDetail || '--' }}</span>    // 应为 receiverAddress
<div v-if="detailData.finishTime">...                  // 应为 completeTime
```

### 修复方案

修改 src/api/admin/order.ts：

```
// AdminOrder 修改
statusDesc?: string           // 改自 statusText
receiverAddress?: string      // 改自 addressDetail
completeTime?: string         // 改自 finishTime

// AdminOrderItem 修改
productCover?: string         // 改自 cover
```

同步修改 OrderManageView.vue 中 3 处模板引用：
- detailData.addressDetail -> detailData.receiverAddress
- detailData.finishTime -> detailData.completeTime
- item.cover -> item.productCover（订单项商品图片）

---

### P0-5：B 端用户列表泛型丢失

**问题位置**：del-common/.../web/vo/AdminUserPageResp.java

### 问题描述

文件：del-common/src/main/java/com/sakana/web/vo/AdminUserPageResp.java

```
@Data
public class AdminUserPageResp {
    private Long total;
    private Integer page;
    private Integer size;
    private List<?> records;   // 通配符，前端无法确定类型
}
```

### 影响

前端 getAdminUserList() 返回 Promise PageResult AdminUser，但 records 实际类型为 ?，TypeScript 编译不报错但类型丢失。

### 修复方案

后端修改 AdminUserPageResp.java，将 List<?> 改为 List AdminUserVO（需确保该类存在于 del-common）。

---

## 二、P1 中等问题

### P1-1：雪花 ID 精度丢失风险

**影响范围**：所有涉及 id / userId / productId / orderId 的接口

| 接口 | 字段 | 现象 |
|------|------|------|
| 登录 POST /api/v1/auth/login | userInfo.id | JSON Long -> JS number 丢失精度 |
| 商品列表 GET /api/v1/products | records[].id | 同上 |
| 订单详情 GET /api/v1/user/orders/{id} | id / userId | 同上 |
| 收货地址 GET /api/v1/user/addresses | id / userId | 同上 |

**当前状态**：前端所有 ID 字段已声明为 string 类型，axios 默认将数字转字符串，风险已被前端吸收。后端可考虑在网关层统一将 Long 序列化为字符串彻底解决。

---

### P1-2：C 端 OrderItem 的 reviewType 来源待确认

**问题**：前端 OrderItem 接口存在 reviewType: 'like' | 'bad' | null 字段，但后端 OrderItemVO 无此字段。

**分析**：reviewType 可能用于前端判断当前用户对订单项是否已投票，该状态应来自评价查询接口 GET /api/v1/reviews/switch，而非订单项本身。

**建议**：前端确认 reviewType 的数据来源，如无实际用途可移除。

---

### P1-3：管理员登录 userInfo 响应待确认

**问题**：前端 LoginResult 接口有 userInfo 字段，后端 LoginResp 也有定义，但实际 admin 登录响应是否返回需验证。

**验证方法**：
curl -X POST http://localhost:10010/api/v1/admin/auth/login -H "Content-Type: application/json" -d '{"username":"superadmin","password":"123456"}'

---

## 三、已确认对齐的接口（无需修改）

| 模块 | 接口 | 对齐状态 |
|------|------|---------|
| 评价投票 | POST /api/v1/orders/{orderId}/items/{productId}/vote | 完全对齐 |
| 评价开关 | GET /api/v1/reviews/switch | 完全对齐 |
| 支付创建 | POST /api/v1/payments | 完全对齐 |
| 运营概览 | GET /api/v1/admin/stats/overview | 完全对齐 |
| 销售趋势 | GET /api/v1/admin/stats/sales | 完全对齐 |
| 热销商品 | GET /api/v1/admin/stats/hot-products | 完全对齐 |
| 商品列表 | GET /api/v1/admin/products | 完全对齐 |
| 商品详情 | GET /api/v1/admin/products/{id} | 完全对齐 |
| 新增/编辑商品 | POST|PUT /api/v1/admin/products | 完全对齐 |
| 商品上下架 | PUT /api/v1/admin/products/{id}/status | 完全对齐 |
| 分类列表 | GET /api/v1/admin/categories | 完全对齐 |
| 新增分类 | POST /api/v1/admin/categories | 完全对齐 |
| 编辑分类 | PUT /api/v1/admin/categories/{id} | 完全对齐 |
| 删除分类 | DELETE /api/v1/admin/categories/{id} | 完全对齐 |
| 评价开关 | PUT /api/v1/admin/reviews/praise\|bad | 完全对齐 |
| 用户列表 | GET /api/v1/admin/users | 结构对齐，泛型丢失（P0-5） |

---

## 四、修复优先级与分工

| 优先级 | 编号 | 问题 | 负责方 | 预计工时 |
|--------|------|------|--------|---------|
| P0 | P0-1 | B端订单 statusText -> statusDesc | 前端 | 5 min |
| P0 | P0-2 | B端订单 addressDetail -> receiverAddress | 前端 | 5 min |
| P0 | P0-3 | B端订单 finishTime -> completeTime | 前端 | 5 min |
| P0 | P0-4 | B端订单项 cover -> productCover | 前端 | 5 min |
| P0 | P0-5 | AdminUserPageResp.records 泛型改为具体类型 | 后端 | 10 min |
| P1 | P1-1 | 雪花 ID 精度问题（已被前端 string 吸收） | 观察 | - |
| P1 | P1-2 | reviewType 来源确认 | 前端确认 | - |
| P1 | P1-3 | admin 登录 userInfo 响应确认 | 后端确认 | - |

---

## 五、关键文件索引

| 文件 | 路径 |
|------|------|
| 前端订单类型 | E:\VSCode_workspace\Delivery\src\api\admin\order.ts |
| 前端订单组件 | E:\VSCode_workspace\Delivery\src\views\admin\OrderManageView.vue |
| 后端 AdminOrderVO | E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\web\vo\AdminOrderVO.java |
| 后端 OrderItemVO | E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\web\vo\OrderItemVO.java |
| 后端 AdminUserPageResp | E:\Idea_project\delivery-cloud\del-common\src\main\java\com\sakana\web\vo\AdminUserPageResp.java |
| 后端 LoginResp | E:\Idea_project\delivery-cloud\del-admin\src\main\java\com\sakana\web\vo\LoginResp.java |

---

报告生成时间：2026-09-03
