# 前后端接口字段对齐审计报告

> **审计日期**：2026-09-03
> **审计范围**：C 端用户侧 + B 端管理后台全部接口
> **前端工作区**：`E:\VSCode_workspace\Delivery`
> **后端服务**：`E:\Idea_project\delivery-cloud`

---

## 一、C 端接口（C端 → 微服务）

### 1.1 登录认证 `POST /api/v1/auth/login`

| 字段 | 前端类型 | 后端类型 | 对齐状态 | 说明 |
|------|---------|---------|---------|------|
| `accessToken` | `string` | `String` | OK | |
| `refreshToken` | `string` | `String` | OK | |
| `expiresIn` | `number` | `Long` | OK | |
| `userInfo.id` | `string` | `Long` | WARN | 雪花 ID 精度风险，JSON 数字反序列化为 number 可能丢失精度 |
| `userInfo.username` | `string` | `String` | OK | |
| `userInfo.nickname` | `string` | `String` | OK | |
| `userInfo.role` | `string` | `String` | OK | |

### 1.2 商品列表 `GET /api/v1/products`

| 字段 | 前端类型 | 后端类型 | 对齐状态 | 说明 |
|------|---------|---------|---------|------|
| `total` | `number` | `Long` | OK | |
| `page` | `number` | `Long` | OK | |
| `size` | `number` | `Long` | OK | |
| `records[].id` | `string` | `Long` | WARN | 雪花 ID 精度风险 |
| `records[].categoryId` | `string` | `Long` | WARN | 同上 |
| `records[].normPrice` | `number` | `BigDecimal` | OK | Jackson 序列化后为数字 |
| `records[].realPrice` | `number` | `BigDecimal` | OK | |

### 1.3 订单创建 `POST /api/v1/user/orders`

| 字段 | 前端 Payload | 后端 OrderCreateReq | 对齐状态 | 说明 |
|------|------------|-------------------|---------|------|
| `addressId` | `string` | `Long` | WARN | axios 自动转字符串，可正常解析 |
| `items[].productId` | `string` | `Long` | WARN | 同上 |
| `items[].quantity` | `number` | `Integer` | OK | |
| `remark` | `string\|undefined` | `String` | OK | |
| `paymentMethod` | **已移除** | 无此字段 | OK | 支付方式已从 payload 中删除 |

### 1.4 订单详情 `GET /api/v1/user/orders/{id}`

| 字段 | 前端 Order | 后端 OrderVO | 对齐状态 | 说明 |
|------|-----------|-------------|---------|------|
| `id` | `string` | `Long` | WARN | 雪花 ID 精度风险 |
| `userId` | `string` | `Long` | WARN | 同上 |
| `totalAmount` | `number` | `BigDecimal` | OK | |
| `items[].id` | **无此字段** | `Long` | WARN | 前端 OrderItem 无 id，后端有 |
| `items[].productId` | `string` | `Long` | WARN | |
| `items[].reviewType` | `'like'\|'bad'\|null` | **无此字段** | WARN | 前端有该字段，后端 OrderItemVO 无 reviewType |
| `createTime` | `string` | `LocalDateTime` | OK | Jackson 序列化为 ISO 字符串 |

### 1.5 收货地址 `GET /api/v1/user/addresses`

| 字段 | 前端 Address | 后端 UserAddressVO | 对齐状态 | 说明 |
|------|------------|-----------------|---------|------|
| `id` | `string` | `Long` | WARN | |
| `userId` | `string\|undefined` | `Long` | WARN | |
| `receiver` | `string` | `String` | OK | |
| `phone` | `string` | `String` | OK | |
| `province/city/district/detail` | `string` | `String` | OK | |
| `fullAddress` | `string\|undefined` | `String` | OK | |
| `isDefault` | `number` | `Integer` | OK | |

### 1.6 评价投票 `POST /api/v1/orders/{orderId}/items/{productId}/vote`

| 字段 | 前端 | 后端 VoteReq | 对齐状态 | 说明 |
|------|-----|------------|---------|------|
| `type` | `'like'\|'bad'\|null` | `String` | OK | 值完全对齐 |
| `orderId` 路径参数 | `string` | `Long` | WARN | axios 转字符串，后端解析 Long |
| `productId` 路径参数 | `string` | `Long` | WARN | 同上 |

### 1.7 评价开关 `GET /api/v1/reviews/switch`

| 字段 | 前端 ReviewSwitch | 后端 ReviewSwitchVO | 对齐状态 | 说明 |
|------|-----------------|-------------------|---------|------|
| `praiseOpen` | `boolean` | `boolean` | OK | |
| `badOpen` | `boolean` | `boolean` | OK | |

### 1.8 支付 `POST /api/v1/payments`

| 字段 | 前端 | 后端 PaymentVO | 对齐状态 | 说明 |
|------|-----|--------------|---------|------|
| `orderNo` | `string` | `String` | OK | |
| `payUrl` | `string` | `String` | OK | |
| `payForm` | `string` | `String` | OK | |

---

## 二、B 端接口（管理后台 → 微服务）

### 2.1 管理员登录 `POST /api/v1/admin/auth/login`

| 字段 | 前端 LoginResult | 后端 LoginResp | 对齐状态 | 说明 |
|------|----------------|--------------|---------|------|
| `accessToken` | `string` | `String` | OK | |
| `refreshToken` | `string` | `String` | OK | |
| `expiresIn` | `number` | `Long` | OK | |
| `userInfo` | `UserInfo` | `UserInfo` | WARN | 需确认实际登录响应是否返回 userInfo |

### 2.2 运营概览 `GET /api/v1/admin/stats/overview`

| 字段 | 前端 OverviewStats | 后端 StatsOverviewVO | 对齐状态 | 说明 |
|------|------------------|-------------------|---------|------|
| `todayOrderCount` | `number` | `Long` | OK | |
| `todaySalesAmount` | `number` | `BigDecimal` | OK | |
| `totalUserCount` | `number` | `Long` | OK | |
| `todayNewUserCount` | `number` | `Long` | OK | |

### 2.3 销售趋势 `GET /api/v1/admin/stats/sales`

| 字段 | 前端 SalesTrendResp | 后端 SalesTrendResp | 对齐状态 | 说明 |
|------|-------------------|-------------------|---------|------|
| `granularity` | `string` | `String` | OK | |
| `points` | `SalesPoint[]` | `List<SalesTrendVO>` | OK | |
| `points[].dateKey` | `string` | `String` | OK | 前端正确使用 `dateKey` |
| `points[].orderCount` | `number` | `Integer` | OK | |
| `points[].salesAmount` | `number` | `BigDecimal` | OK | |

### 2.4 热销商品 `GET /api/v1/admin/stats/hot-products`

| 字段 | 前端 HotProduct | 后端 HotProductVO | 对齐状态 | 说明 |
|------|--------------|----------------|---------|------|
| `id` | `string` | `Long` | WARN | |
| `name` | `string` | `String` | OK | |
| `cover` | `string` | `String` | OK | |
| `realPrice` | `number` | `BigDecimal` | OK | |
| `sales` | `number` | `Integer` | OK | |
| `stock` | **无此字段** | `Integer` | WARN | 后端返回，前端未定义 |
| `categoryId` | **无此字段** | `Long` | WARN | 后端返回，前端未定义 |
| `status` | **无此字段** | `Integer` | WARN | 后端返回，前端未定义 |

### 2.5 管理员订单列表 `GET /api/v1/admin/orders`

| 字段 | 前端 AdminOrder | 后端 AdminOrderVO | 对齐状态 | 说明 |
|------|--------------|-----------------|---------|------|
| `id` | `string` | `Long` | WARN | |
| `statusText` | `string` | `statusDesc` | FAIL | **字段名不匹配** |
| `addressDetail` | `string` | `receiverAddress` | FAIL | **字段名不匹配** |
| `finishTime` | `string` | `completeTime` | FAIL | **字段名不匹配** |
| `payAmount` | **无此字段** | `BigDecimal` | WARN | 后端返回，前端未定义 |
| `deliveryTime` | **无此字段** | `LocalDateTime` | WARN | 后端返回，前端未定义 |
| `items[].cover` | `string\|undefined` | **无此字段（实际为 productCover）** | FAIL | 前端定义 `cover`，后端为 `productCover` |

### 2.6 管理员订单详情 `GET /api/v1/admin/orders/{id}`

同 2.5，字段完全一致。

### 2.7 管理员订单状态变更 `PUT /api/v1/admin/orders/{id}/status`

| 字段 | 前端参数 | 后端 AdminOrderStatusReq | 对齐状态 | 说明 |
|------|--------|----------------------|---------|------|
| `status` | `number` | `Integer` | OK | |
| `remark` | **无此字段** | `String` | WARN | 前端不传 remark，后端支持但非必填 |

### 2.8 管理员商品列表 `GET /api/v1/admin/products`

| 字段 | 前端 AdminProduct | 后端 ProductVO（通过 ProductPageResp） | 对齐状态 | 说明 |
|------|-----------------|------------------------------------|---------|------|
| `likeCount` | `number\|undefined` | `Integer` | OK | |
| `dislikeCount` | `number\|undefined` | `Integer` | OK | |
| 其余字段 | — | — | OK | |

### 2.9 管理员新增/编辑商品 `POST|PUT /api/v1/admin/products`

| 字段 | 前端 ProductCreatePayload | 后端 ProductReq | 对齐状态 | 说明 |
|------|-------------------------|---------------|---------|------|
| `name` | `string` | `@NotBlank` | OK | |
| `categoryId` | `string\|number` | `@NotNull Long` | OK | |
| `cover` | `string` | `String` | OK | |
| `description` | `string` | `String` | OK | |
| `normPrice` | `number` | `@NotNull BigDecimal` | OK | |
| `realPrice` | `number` | `@NotNull BigDecimal` | OK | |
| `stock` | `number` | `@NotNull Integer` | OK | |
| `status` | `number\|undefined` | `Integer`（默认 0） | OK | |

### 2.10 管理员商品上下架 `PUT /api/v1/admin/products/{id}/status`

| 字段 | 前端参数 | 后端 ProductStatusReq | 对齐状态 | 说明 |
|------|--------|---------------------|---------|------|
| `status` | `number` | `@NotNull Integer` | OK | |

### 2.11 管理员分类 `GET /api/v1/admin/categories`

| 字段 | 前端 AdminCategory | 后端 CategoryVO | 对齐状态 | 说明 |
|------|-----------------|---------------|---------|------|
| `id` | `string` | `Long` | WARN | |
| `sort` | `number` | `Integer` | OK | |
| `status` | `number` | `Integer` | OK | |
| `name` | `string` | `String` | OK | |

### 2.12 管理员新增分类 `POST /api/v1/admin/categories`

| 字段 | 前端 CategoryPayload | 后端 CategoryReq | 对齐状态 | 说明 |
|------|--------------------|----------------|---------|------|
| `name` | `string` | `@NotBlank String` | OK | |
| `sort` | `number` | `Integer`（默认 0） | OK | |
| `status` | `number\|undefined` | `Integer`（默认 0） | OK | |

### 2.13 管理员用户列表 `GET /api/v1/admin/users`

| 字段 | 前端 AdminUser | 后端 AdminUserPageResp | 对齐状态 | 说明 |
|------|--------------|----------------------|---------|------|
| `records` | `AdminUser[]` | `List<?>` | FAIL | **严重：后端用通配符，前端无法确定类型** |
| `page` | `number` | `Integer` | OK | |
| `size` | `number` | `Integer` | OK | |
| `total` | `number` | `Long` | OK | |

### 2.14 管理员评价开关 `PUT /api/v1/admin/reviews/praise|bad`

| 字段 | 前端参数 | 后端 ReviewSwitchReq | 对齐状态 | 说明 |
|------|--------|-------------------|---------|------|
| `open` | `1\|0`（Integer） | `Integer` | OK | 前端传 `open ? 1 : 0`，后端 `Integer` |

---

## 三、问题汇总

### FAIL 严重问题（会导致功能错误）

| # | 接口 | 问题 | 修复方向 |
|---|------|------|---------|
| 1 | B 端订单列表/详情 | `statusText`（前端）vs `statusDesc`（后端）字段名不匹配 | 前端修复 |
| 2 | B 端订单列表/详情 | `addressDetail`（前端）vs `receiverAddress`（后端）字段名不匹配 | 前端修复 |
| 3 | B 端订单列表/详情 | `finishTime`（前端）vs `completeTime`（后端）字段名不匹配 | 前端修复 |
| 4 | B 端订单列表/详情 | `items[].cover`（前端）vs `items[].productCover`（后端） | 前端修复 |
| 5 | B 端用户列表 | `AdminUserPageResp.records` 类型为 `List<?>`（通配符） | 后端修复 |

### WARN 中等问题（可能导致精度丢失或运行时行为不确定）

| # | 接口 | 问题 | 修复方向 |
|---|------|------|---------|
| 6 | 所有涉及 ID 的接口 | `id/userId/productId` 等雪花 ID（Long）JSON 反序列化为 number 后精度可能丢失 | 前端全局注意用 string；后端考虑 Jackson 配置 |
| 7 | C 端 OrderItem | 前端有 `reviewType` 字段，后端 OrderItemVO 无此字段 | 待确认评价状态来源 |
| 8 | 管理员登录 | `userInfo` 字段在 LoginResp 中有定义，但实际 adminLogin 响应是否返回需验证 | 后端确认 |

### 次要问题（多余字段不影响功能）

| # | 接口 | 问题 | 修复方向 |
|---|------|------|---------|
| 9 | 热销商品 | 后端返回 `stock/categoryId/status`，前端未定义 | 可接受 |
| 10 | B 端订单 | 后端返回 `payAmount/deliveryTime/remark`，前端未定义 | 可接受 |

---

## 四、字段名不匹配详细对照（B端订单 AdminOrder）

| 后端真实字段名 | 前端使用字段名 | 状态 |
|-------------|-------------|------|
| `statusDesc` | `statusText` | FAIL 需修复 |
| `receiverAddress` | `addressDetail` | FAIL 需修复 |
| `completeTime` | `finishTime` | FAIL 需修复 |
| `items[].productCover` | `items[].cover` | FAIL 需修复 |

---

## 五、修复优先级建议

| 优先级 | 问题编号 | 说明 |
|--------|---------|------|
| **P0** | #1 ~ #4 | B 端订单字段名不匹配，会导致页面显示空白或数据错误 |
| **P1** | #5 | B 端用户列表泛型丢失，需后端修复 |
| **P2** | #6 | 雪花 ID 精度问题（中等概率触发，高影响力） |
| **P3** | #7 ~ #8 | 待确认，不一定需要修复 |