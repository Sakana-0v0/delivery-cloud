# 外卖微服务 - API 接口文档

**版本**：v3.0  
**更新日期**：2026-09-01  
**基础路径**：http://localhost:9000（网关端口）

---

## 📌 通用说明

### 1. 认证方式

| 类型 | Header | 示例 |
|------|--------|------|
| C端用户 | Authorization: Bearer <accessToken> | Authorization: Bearer eyJhbGciOiJIUzI1NiJ9... |
| 管理后台 | Authorization: Bearer <adminAccessToken> | 同上，token 中 role 为 ADMIN |
| 内部API | X-Internal-Service-Token: internal-service-secret-key-2024 | 仅服务间调用 |

### 2. 统一响应格式

`json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
`

| code | 说明 |
|------|------|
| 0 | 成功 |
| ≠0 | 失败，msg 包含错误信息 |

### 3. Long 类型处理
- **后端返回**：Long 类型（如雪花ID）
- **前端接收**：使用 **string** 避免精度丢失
- 示例："id": "1847980379520"

### 4. 分页响应格式

`json
{
  "code": 0,
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "records": []
  }
}
`

### 5. 错误码概览

| 错误码段 | 说明 |
|----------|------|
| 2000-2999 | 认证相关错误 |
| 3000-3999 | 业务逻辑错误 |
| 5000-5999 | 系统内部错误 |

---

## 一、用户端 API（C端）

### 1.1 认证服务

**路由**：/api/v1/auth/** → del-user

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 发送验证码 | POST | /api/v1/auth/send-code | ❌ |
| 2 | 用户注册 | POST | /api/v1/auth/register | ❌ |
| 3 | 用户登录 | POST | /api/v1/auth/login | ❌ |
| 4 | 刷新Token | POST | /api/v1/auth/refresh | ❌ |
| 5 | 登出 | POST | /api/v1/auth/logout | ✅ |

**发送验证码**
`http
POST /api/v1/auth/send-code
Content-Type: application/json

{ "email": "user@example.com" }
`

**用户注册**
`http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "Password123!",
  "email": "user@example.com",
  "nickname": "张三",
  "code": "123456"
}
`

**用户登录**
`http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "Password123!"
}
`
**响应**
`json
{
  "code": 0,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "userInfo": {
      "id": "1847980379520",
      "username": "zhangsan",
      "nickname": "张三",
      "role": "USER"
    }
  }
}
`

**刷新Token**
`http
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
`

**登出**
`http
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
`

---

### 1.2 收货地址

**路由**：/api/v1/addresses/** → del-user

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 地址列表 | GET | /api/v1/user/addresses | ✅ |
| 2 | 地址详情 | GET | /api/v1/user/addresses/{id} | ✅ |
| 3 | 新增地址 | POST | /api/v1/user/addresses | ✅ |
| 4 | 修改地址 | PUT | /api/v1/user/addresses/{id} | ✅ |
| 5 | 删除地址 | DELETE | /api/v1/user/addresses/{id} | ✅ |
| 6 | 设为默认 | PUT | /api/v1/user/addresses/{id}/default | ✅ |

---

### 1.3 商品服务

**路由**：/api/v1/products/** → del-product

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 商品列表 | GET | /api/v1/products | ❌ |
| 2 | 商品详情 | GET | /api/v1/products/{id} | ❌ |
| 3 | 热门商品 | GET | /api/v1/products/hot | ❌ |

**商品列表**
`http
GET /api/v1/products?categoryId=1&keyword=汉堡&page=1&size=10
`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| categoryId | Long | 否 | 分类ID |
| keyword | String | 否 | 关键词搜索 |
| page | Integer | 否 | 默认1 |
| size | Integer | 否 | 默认10 |

**商品详情**
`http
GET /api/v1/products/{id}
`

**热门商品**
`http
GET /api/v1/products/hot?limit=10
`

---

### 1.4 分类服务

**路由**：/api/v1/categories/** → del-product

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 分类列表 | GET | /api/v1/categories | ❌ |

---

### 1.5 购物车服务

**路由**：/api/v1/cart/** → del-order

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 查看购物车 | GET | /api/v1/cart | ✅ |
| 2 | 添加商品 | POST | /api/v1/cart/items | ✅ |
| 3 | 修改数量 | PUT | /api/v1/cart/items/{productId} | ✅ |
| 4 | 删除商品 | DELETE | /api/v1/cart/items/{productId} | ✅ |
| 5 | 清空购物车 | DELETE | /api/v1/cart | ✅ |

**添加商品**
`http
POST /api/v1/cart/items
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "productId": "1847980379520",
  "quantity": 2
}
`

**修改数量**
`http
PUT /api/v1/cart/items/{productId}
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "quantity": 3 }
`

---

### 1.6 订单服务

**路由**：/api/v1/orders/** → del-order

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 创建订单 | POST | /api/v1/user/orders | ✅ |
| 2 | 我的订单 | GET | /api/v1/user/orders | ✅ |
| 3 | 订单详情 | GET | /api/v1/user/orders/{id} | ✅ |
| 4 | 取消订单 | POST | /api/v1/user/orders/{id}/cancel | ✅ |
| 5 | 确认收货 | POST | /api/v1/user/orders/{id}/confirm | ✅ |

**创建订单**
`http
POST /api/v1/user/orders
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "addressId": "1847980379520",
  "remark": "少放辣",
  "items": [
    { "productId": "1847980379520", "quantity": 2 },
    { "productId": "1847980379521", "quantity": 1 }
  ]
}
`

**我的订单**
`http
GET /api/v1/user/orders?status=1&page=1&size=10
Authorization: Bearer <accessToken>
`

| status | 说明 |
|--------|------|
| 1 | 待支付 |
| 2 | 已支付 |
| 3 | 配送中 |
| 4 | 已完成 |
| 5 | 已取消 |

---

### 1.7 支付服务

**路由**：/api/v1/payments/** → del-payment

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 创建支付 | POST | /api/v1/payments | ✅ |
| 2 | 支付状态 | GET | /api/v1/payments/{orderNo} | ✅ |
| 3 | 支付回调 | POST | /api/v1/payments/notify | ❌ |
| 4 | 支付跳转 | GET | /api/v1/payments/return | ❌ |

**创建支付**
`http
POST /api/v1/payments
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "orderId": "1847980379520" }
`

---

### 1.8 商品评价（点赞/点踩）

**路由**：/api/v1/orders/*/items/*/vote → del-product

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 点赞/点踩 | POST | /api/v1/orders/{orderId}/items/{productId}/vote | ✅ |

**点赞/点踩**
`http
POST /api/v1/orders/{orderId}/items/{productId}/vote
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "type": "like" }
`

| type | 说明 |
|------|------|
| like | 点赞 |
| ad | 点踩 |
| 
ull 或不传 | 取消 |

---

### 1.9 站内消息

**路由**：/api/v1/messages/** → del-message

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 我的消息 | GET | /api/v1/messages | ✅ |
| 2 | 未读数 | GET | /api/v1/messages/unread-count | ✅ |
| 3 | 标记已读 | PUT | /api/v1/messages/{id}/read | ✅ |
| 4 | 全部已读 | PUT | /api/v1/messages/read-all | ✅ |

---

### 1.10 文件上传

**路由**：/api/v1/files/** → del-file

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 上传文件 | POST | /api/v1/files/upload | ✅ |
| 2 | 文件信息 | GET | /api/v1/files/info | ✅ |
| 3 | 删除文件 | DELETE | /api/v1/files/{md5} | ✅ |

**上传文件**
`http
POST /api/v1/files/upload
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data

file: (binary)
`

---

## 二、管理后台 API（B端）

**路由**：/api/v1/admin/** → del-admin

### 2.1 管理员认证

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 管理员登录 | POST | /api/v1/admin/auth/login | ❌ |
| 2 | 当前信息 | GET | /api/v1/admin/auth/me | ✅ |
| 3 | 管理员登出 | POST | /api/v1/admin/auth/logout | ✅ |

**管理员登录**
`http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
`

---

### 2.2 商品管理

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 商品分页 | GET | /api/v1/admin/products | ✅ |
| 2 | 商品详情 | GET | /api/v1/admin/products/{id} | ✅ |
| 3 | 新增商品 | POST | /api/v1/admin/products | ✅ |
| 4 | 修改商品 | PUT | /api/v1/admin/products/{id} | ✅ |
| 5 | 删除商品 | DELETE | /api/v1/admin/products/{id} | ✅ |
| 6 | 上下架 | PUT | /api/v1/admin/products/{id}/status | ✅ |
| 7 | 调整库存 | PUT | /api/v1/admin/products/{id}/stock | ✅ |

---

### 2.3 分类管理

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 分类分页 | GET | /api/v1/admin/categories | ✅ |
| 2 | 新增分类 | POST | /api/v1/admin/categories | ✅ |
| 3 | 修改分类 | PUT | /api/v1/admin/categories/{id} | ✅ |
| 4 | 删除分类 | DELETE | /api/v1/admin/categories/{id} | ✅ |

---

### 2.4 订单管理

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 订单分页 | GET | /api/v1/admin/orders | ✅ |
| 2 | 订单详情 | GET | /api/v1/admin/orders/{id} | ✅ |
| 3 | 修改状态 | PUT | /api/v1/admin/orders/{id}/status | ✅ |

---

### 2.5 用户管理

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 用户分页 | GET | /api/v1/admin/users | ✅ |
| 2 | 用户详情 | GET | /api/v1/admin/users/{id} | ✅ |
| 3 | 冻结/解冻 | PUT | /api/v1/admin/users/{id}/status | ✅ |

---

### 2.6 评价管理

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 开关状态 | GET | /api/v1/admin/reviews/switch | ✅ |
| 2 | 👍开关 | PUT | /api/v1/admin/reviews/praise | ✅ |
| 3 | 👎开关 | PUT | /api/v1/admin/reviews/bad | ✅ |

**设置评价开关**
`http
PUT /api/v1/admin/reviews/praise
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{ "open": 1 }  // 1=开启, 0=关闭
`

---

### 2.7 运营统计

| # | 接口 | 方法 | 路径 | 认证 |
|---|------|------|------|------|
| 1 | 数据概览 | GET | /api/v1/admin/stats/overview | ✅ |
| 2 | 销售趋势 | GET | /api/v1/admin/stats/sales | ✅ |
| 3 | 热卖商品 | GET | /api/v1/admin/stats/hot-products | ✅ |

---

## 三、内部API（服务间调用）

### 3.1 订单统计

| # | 接口 | 方法 | 路径 |
|---|------|------|------|
| 1 | 今日统计 | GET | /internal/stats/today |
| 2 | 销售趋势 | GET | /internal/stats/sales-trend |

### 3.2 用户统计

| # | 接口 | 方法 | 路径 |
|---|------|------|------|
| 1 | 用户统计 | GET | /internal/stats/users |

### 3.3 商品统计

| # | 接口 | 方法 | 路径 |
|---|------|------|------|
| 1 | 热卖商品 | GET | /internal/stats/hot-products |

---

## 📊 服务与路由对照表

| 网关路径 | 目标服务 | 后端实际路径 |
|----------|----------|--------------|
| /api/v1/auth/** | del-user | /api/v1/auth |
| /api/v1/addresses/** | del-user | /api/v1/user/addresses |
| /api/v1/products/** | del-product | /api/v1/products |
| /api/v1/categories/** | del-product | /api/v1/categories |
| /api/v1/orders/*/items/*/vote | del-product | /api/v1/orders |
| /api/v1/orders/** | del-order | /api/v1/user/orders |
| /api/v1/cart/** | del-order | /api/v1/cart |
| /api/v1/payments/** | del-payment | /api/v1/payments |
| /api/v1/messages/** | del-message | /api/v1/messages |
| /api/v1/files/** | del-file | /api/v1/files |
| /api/v1/admin/** | del-admin | /api/v1/admin/... |

---

**文档版本**：v3.0  
**更新历史**：
- v1.0：初始版本（基于旧单体代码）
- v2.0：更新路由配置，添加 ⚠️ 标记
- v3.0：修复 del-file 路径问题，移除 ⚠️ 标记
