# 📋 后端返回前端 JSON 字段清单

> **统计时间**：2026-09-03  
> **统一响应格式**：`{ "code": 0, "message": "ok", "source": "service-name", "data": <T>, "timestamp": 1716000000000 }`

---

## 🟢 统一外层字段（每个接口都有）

| 字段 | 类型 | 说明 |
| | | |
| `code` | Integer | 业务状态码：0=成功 |
| `message` | String | 提示信息 |
| `source` | String | 来源服务名 |
| `data` | T | 业务数据 |
| `timestamp` | Long | 响应时间戳（毫秒） |

---

## 🔵 1. 用户模块 (del-user)

### 1.1 认证接口 `/api/v1/auth/`

#### `POST /api/v1/auth/register`
```json
{ "code":0, "message":"ok", "data": null }
```

#### `POST /api/v1/auth/send-code`
```json
{ "code":0, "message":"ok", "data": null }
```

#### `POST /api/v1/auth/login` → `R<LoginResp>`
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "expiresIn": 7200,
  "userInfo": { "id": 123, "username":"sakana", "nickname":"昵称", "role":"USER" }
}
```

#### `POST /api/v1/auth/refresh` → `R<LoginResp>` (同 login)

#### `POST /api/v1/auth/logout`
```json
{ "code":0, "message":"ok", "data": null }
```

### 1.2 收货地址 `/api/v1/user/addresses/`

#### `GET /api/v1/user/addresses` → `R<List<UserAddressVO>>`
```json
[{
  "id": 1,
  "userId": 123,
  "receiver": "张三",
  "phone": "13878789999",
  "province": "湖南省",
  "city": "衡阳市",
  "district": "蒸湘区",
  "detail": "某街道123号",
  "fullAddress": "湖南省衡阳市蒸湘区某街道123号",
  "isDefault": 1,
  "username": "sakana",
  "nickname": "昵称",
  "createTime": "2026-09-02T14:00:00"
}]
```

#### `GET /api/v1/user/addresses/{id}` → `R<UserAddressVO>` (同上)

#### `POST /api/v1/user/addresses` → `R<Long>`
```json
{ "code":0, "data": 1 }
```

#### `PUT/DELETE /api/v1/user/addresses/{id}` → `R<Void>`

---

## 🟡 2. 商品模块 (del-product)

### 2.1 商品 `/api/v1/products/`

#### `GET /api/v1/products` → `R<ProductPageResp>`
```json
{
  "total": 18, "page": 1, "size": 10,
  "records": [{
    "id": 1, "categoryId": 2, "categoryName": "精品小炒",
    "name": "酸辣凉拌土豆丝", "cover": "http://...",
    "description": "酸辣爽口",
    "normPrice": 22.00, "realPrice": 20.00,
    "stock": 99, "sales": 0, "status": 0,
    "likeCount": 0, "dislikeCount": 0
  }]
}
```

#### `GET /api/v1/products/{id}` → `R<ProductVO>` (单条记录，同上 records)

#### `GET /api/v1/products/{id}/snapshot` → `R<ProductSnapshotVO>`
```json
{
  "id": 1, "categoryId": 2, "name": "...",
  "cover": "http://...",
  "normPrice": 22.00, "realPrice": 20.00,
  "stock": 99, "sales": 0, "status": 0
}
```

### 2.2 分类 `/api/v1/categories/`

#### `GET /api/v1/categories` → `R<List<CategoryVO>>`
```json
[{
  "id": 1, "name": "招牌主菜", "sort": 1, "status": 0
}]
```

### 2.3 评价 `/api/v1/reviews/`

#### `GET /api/v1/reviews/mine` → `R<ReviewSwitchVO>`
```json
{ "praiseOpen": true, "badOpen": true }
```

#### `GET /api/v1/reviews/switch` → `R<ReviewSwitchVO>` (同上)

#### `GET /api/v1/reviews/mine` (新增) → `R<ReviewVO>`
```json
{
  "id": 1, "orderId": 100, "productId": 1,
  "type": "LIKE", "createTime": "2026-09-02T14:00:00"
}
```

---

## 🟠 3. 订单模块 (del-order)

### 3.1 购物车 `/api/v1/cart/`

#### `GET /api/v1/cart` → `R<CartVO>`
```json
{
  "items": [{
    "productId": 1, "name": "酸辣凉拌土豆丝",
    "cover": "http://...", "price": 20.00,
    "quantity": 2, "subtotal": 40.00
  }],
  "totalAmount": 40.00,
  "itemCount": 1
}
```

#### `POST /api/v1/cart/items` → `R<Void>`

#### `DELETE /api/v1/cart/items/{productId}` → `R<Void>`

#### `DELETE /api/v1/cart` → `R<Void>` (清空)

### 3.2 订单 `/api/v1/user/orders/`

#### `POST /api/v1/user/orders` → `R<OrderVO>`
```json
{
  "id": 123, "orderNo": "ORD202609021428065359",
  "userId": 123, "totalAmount": 20.00,
  "status": 1, "statusDesc": "待支付",
  "receiverName": "张三", "receiverPhone": "13878789999",
  "receiverAddress": "湖南省...",
  "remark": null,
  "paymentDeadline": null,
  "payTime": null, "shipTime": null,
  "completeTime": null, "cancelTime": null,
  "createTime": "2026-09-02T14:28:06",
  "items": [{
    "id": 1, "productId": 1,
    "productName": "...", "productCover": "...",
    "productPrice": 20.00, "quantity": 1,
    "subtotalAmount": 20.00
  }]
}
```

#### `GET /api/v1/user/orders` → `R<OrderPageResp>`
```json
{
  "total": 10, "page": 1, "size": 10,
  "records": [/* OrderVO[] */]
}
```

#### `GET /api/v1/user/orders/{id}` → `R<OrderVO>`

#### `POST /api/v1/user/orders/{id}/cancel` → `R<Void>`

#### `POST /api/v1/user/orders/{id}/confirm` → `R<Void>`

---

## 🔴 4. 支付模块 (del-payment)

### 4.1 `/api/v1/payments/`

#### `POST /api/v1/payments` → `R<PaymentVO>`
```json
{
  "orderNo": "ORD202609021428065359",
  "payUrl": "https://openapi.alipay.com/gateway.do?...",
  "payForm": "<form>...</form>"
}
```

#### `GET /api/v1/payments/{orderNo}` → `R<PaymentStatusVO>`
```json
{
  "orderNo": "ORD20260902...",
  "payNo": "PAY2026...",
  "status": 2,
  "paidAt": "2026-09-02T14:30:00",
  "amount": 20.00,
  "tradeNo": "alipay-trade-123",
  "buyerUserId": "2088...",
  "buyerLogonId": "user@example.com"
}
```

---

## 🟣 5. 文件模块 (del-file)

### 5.1 `/api/v1/files/`

#### `POST /api/v1/files/upload` → `R<FileUploadVO>`
```json
{
  "md5": "0fae83da2b1fc73cd6242a335c9fe7dc",
  "fileUrl": "http://127.0.0.1:9000/product-picture/2026-09-01/xxx.jpg",
  "originalName": "500008.jpg",
  "fileSize": 102400,
  "contentType": "image/jpeg",
  "deduplicated": false
}
```

---

## 🟤 6. 消息模块 (del-message)

### 6.1 `/api/v1/messages/`

#### `PUT /api/v1/messages/{id}/read` → `R<Void>`

#### `PUT /api/v1/messages/read-all` → `R<Void>`

---

## ⚪ 7. 管理后台 (del-admin)

### 7.1 认证 `/api/v1/admin/auth/`

#### `POST /api/v1/admin/auth/login` → `R<AdminLoginResp>`
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "expiresIn": 7200,
  "userInfo": { "id": 1, "username":"superadmin", "nickname":"超管", "role":"SUPER_ADMIN" }
}
```

#### `GET /api/v1/admin/auth/me` → `R<AdminLoginResp.UserInfo>`
```json
{ "id": 1, "username":"superadmin", "nickname":"超管", "role":"SUPER_ADMIN" }
```

#### `POST /api/v1/admin/auth/logout` → `R<Void>`

### 7.2 地址管理 `/api/v1/admin/addresses/`

#### `DELETE /api/v1/admin/addresses/{id}` → `R<Void>`

### 7.3 用户管理 `/api/v1/admin/users/`

#### `GET /api/v1/admin/users` → `R<AdminUserPageResp>`
```json
{
  "total": 4, "page": 1, "size": 10,
  "records": [/* AdminUserVO[] */]
}
```

#### `GET /api/v1/admin/users/{id}` → `R<Object>` (User 实体)

#### `PUT /api/v1/admin/users/{id}/status` → `R<Void>`

### 7.4 订单管理 `/api/v1/admin/orders/`

#### `GET /api/v1/admin/orders` → `R<AdminOrderPageResp>`
```json
{
  "total": 10, "page": 1, "size": 10,
  "records": [{
    "id": 1, "orderNo": "ORD...",
    "userId": 123, "username": "sakana",
    "nickname": "昵称",
    "totalAmount": 20.00, "payAmount": 20.00,
    "status": 1, "statusDesc": "待支付",
    "receiverName": "...", "receiverPhone": "...",
    "receiverAddress": "...",
    "remark": null,
    "deliveryTime": null, "payTime": null,
    "shipTime": null, "completeTime": null,
    "cancelTime": null, "createTime": "...",
    "items": [/* OrderItemVO[] */]
  }]
}
```

#### `GET /api/v1/admin/orders/{id}` → `R<AdminOrderVO>`

#### `PUT /api/v1/admin/orders/{id}/status` → `R<Void>`

### 7.5 商品管理 `/api/v1/admin/products/`

#### `GET /api/v1/admin/products/{id}` → `R<ProductVO>`

#### `POST/PUT/DELETE /api/v1/admin/products[/{id}]` → `R<Void>`

#### `PUT /api/v1/admin/products/{id}/status` → `R<Void>`

#### `PUT /api/v1/admin/products/{id}/stock` → `R<Void>`

### 7.6 分类管理 `/api/v1/admin/categories/`

#### `POST/PUT/DELETE /api/v1/admin/categories[/{id}]` → `R<Void>`

### 7.7 评价开关 `/api/v1/admin/reviews/`

#### `PUT /api/v1/admin/reviews/praise` → `R<Void>` (开关点赞)

#### `PUT /api/v1/admin/reviews/bad` → `R<Void>` (开关点踩)

#### `GET /api/v1/admin/reviews/switch` → `R<ReviewSwitchVO>`
```json
{ "praiseOpen": true, "badOpen": true }
```

### 7.8 统计 `/api/v1/admin/stats/`

#### `GET /api/v1/admin/stats/overview` → `R<StatsOverviewVO>`
```json
{
  "todayOrderCount": 5,
  "todaySalesAmount": 100.00,
  "totalUserCount": 9,
  "todayNewUserCount": 1
}
```

#### `GET /api/v1/admin/stats/hot-products` → `R<List<HotProductVO>>`
```json
[{
  "id": 1, "name": "...", "cover": "...",
  "realPrice": 20.00, "stock": 99,
  "sales": 0, "categoryId": 2, "status": 0
}]
```

---

## 📊 字段命名约定

| 模式 | 说明 | 示例 |
| | | |
| 时间字段 | ISO-8601 LocalDateTime | `createTime`, `payTime` |
| 金额字段 | BigDecimal | `totalAmount`, `realPrice` |
| ID 字段 | Long | `id`, `userId`, `productId` |
| 状态字段 | Integer (枚举码) | `status`, `isDefault` |
| 外层状态码 | Integer (0=成功) | `code` |
