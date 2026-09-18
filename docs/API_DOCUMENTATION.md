# 外卖系统后端接口文档

> **基础 URL**: http://localhost:10010（网关端口）  
> **认证方式**: Authorization: Bearer <JWT>（登录后获取）  
> **响应格式**: 统一 R<T> 包装，详见文末"通用响应结构"

---

## 一、认证模块 /api/v1/auth

### 1.1 用户注册
`
POST /api/v1/auth/register
Content-Type: application/json

Body:
{
  "username": "string",      // 用户名（必填，4-20位）
  "password": "string",      // 密码（必填，6位以上）
  "phone": "string"          // 手机号（必填）
}

Response: R<Void>
成功: code=0, message="ok"
失败: code=3xxx, message="用户名已存在"等
`

### 1.2 发送验证码
`
POST /api/v1/auth/send-code
Content-Type: application/json

Body:
{
  "phone": "string"          // 手机号（必填）
}

Response: R<Void>
`

### 1.3 用户登录
`
POST /api/v1/auth/login
Content-Type: application/json

Body:
{
  "username": "string",      // 用户名或手机号
  "password": "string"       // 密码
}

Response: R<LoginResp>
{
  "code": 0,
  "data": {
    "accessToken": "eyJ...",   // JWT access token（前端存储）
    "refreshToken": "eyJ...",  // 刷新token
    "expiresIn": 86400,
    "userInfo": {
      "id": 2087088327483965445,
      "username": "sakana",
      "nickname": "sakana",
      "role": "USER"           // USER | ADMIN | SUPER_ADMIN
    }
  }
}
`

### 1.4 刷新 Token
`
POST /api/v1/auth/refresh
Content-Type: application/json

Body:
{
  "refreshToken": "eyJ..."     // 登录时获取的 refreshToken
}
`

### 1.5 退出登录
`
POST /api/v1/auth/logout
Authorization: Bearer <token>

Response: R<Void>
`

---

## 二、用户地址 /api/v1/user/addresses

> 需要 USER 角色认证

### 2.1 获取地址列表
`
GET /api/v1/user/addresses
Authorization: Bearer <token>

Response: R<List<UserAddressVO>>
{
  "code": 0,
  "data": [
    {
      "id": "4",
      "userId": "2087088327483965445",
      "receiver": "张三",
      "phone": "13878789999",
      "province": "湖南省",
      "city": "衡阳市",
      "district": "蒸湘区",
      "detail": "某街道123号",
      "fullAddress": "湖南省衡阳市蒸湘区某街道123号",
      "isDefault": 1,
      "createTime": "2026-09-03T16:10:43"
    }
  ]
}
`

### 2.2 获取单个地址
`
GET /api/v1/user/addresses/{id}
Authorization: Bearer <token>

Response: R<UserAddressVO>
`

### 2.3 新增地址
`
POST /api/v1/user/addresses
Authorization: Bearer <token>
Content-Type: application/json

Body:
{
  "receiver": "string",        // 收货人姓名（必填）
  "phone": "string",           // 手机号（必填）
  "province": "string",        // 省（必填）
  "city": "string",            // 市（必填）
  "district": "string",        // 区（必填）
  "detail": "string",          // 详细地址（必填）
  "isDefault": 0               // 是否默认 0/1
}
`

### 2.4 更新地址
`
PUT /api/v1/user/addresses/{id}
Authorization: Bearer <token>
Content-Type: application/json

Body: 同新增
`

### 2.5 删除地址
`
DELETE /api/v1/user/addresses/{id}
Authorization: Bearer <token>

Response: R<Void>
`

### 2.6 设置默认地址
`
PUT /api/v1/user/addresses/{id}/default
Authorization: Bearer <token>

Response: R<Void>
`

---

## 三、菜品模块 /api/v1/products

### 3.1 分页查询菜品
`
GET /api/v1/products
Authorization: Bearer <token>（可选，不带也能访问）

Query 参数:
  categoryId  Long    分类ID（可选）
  keyword     String  关键词搜索（可选）
  page        int     页码（默认1）
  size        int     每页条数（默认10）

Response: R<Page<ProductVO>>
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": "1",
        "categoryId": "2",
        "categoryName": "汤品",
        "name": "番茄鱼汤",
        "cover": "http://127.0.0.1:9000/product-picture/500023.jpg",
        "description": "新鲜番茄配嫩滑鱼肉...",
        "normPrice": 45.00,
        "realPrice": 40.00,
        "stock": 99,
        "sales": 256,
        "status": 1,
        "likeCount": 128,
        "dislikeCount": 3
      }
    ],
    "total": 96,
    "size": 10,
    "current": 1
  }
}
`

### 3.2 菜品详情
`
GET /api/v1/products/{id}
Authorization: Bearer <token>（可选）

Response: R<ProductVO>
`

### 3.3 菜品快照（创建订单前获取最新价格）【⚠️已对齐后端实测】
`
GET /api/v1/products/{id}/snapshot
Authorization: Bearer <token>

Response: R<ProductVO>（⚠️ 后端实测返回完整 ProductVO，不是简化的 SnapshotVO）
{
  "id": "1",          // ⚠️ 字段名是 id（不是 productId）
  "name": "番茄鱼汤",
  "cover": "...",
  "normPrice": 45.00,         // ⚠️ 后端没有独立 price 字段，使用 normPrice/realPrice
    "realPrice": 40.00,        // 当前价格（用于下单），用 realPrice
  "stock": 99
}
`

### 3.4 热销菜品
`
GET /api/v1/products/hot?limit=10

Query: limit int 返回数量（默认10）

Response: R<List<ProductVO>>
`

---

## 四、菜品分类 /api/v1/categories

### 4.1 获取全部分类
`
GET /api/v1/categories
Authorization: Bearer <token>（可选）

Response: R<List<CategoryVO>>
{
  "code": 0,
  "data": [
    { "id": "1", "name": "主食", "sort": 1 },
    { "id": "2", "name": "汤品", "sort": 2 },
    { "id": "3", "name": "小菜", "sort": 3 }
  ]
}
`

---

## 五、菜品评价 /api/v1/reviews

### 5.1 评价开关查询【⚠️原文档 POST 接口未实现】
`
GET /api/v1/reviews/switch
Authorization: Bearer <token>

Response: R<ReviewSwitchVO>
{
  "code": 0,
  "data": {
    "praiseOpen": true,     // 点赞功能开关
    "badOpen": true         // 点踩功能开关
  }
}

> ⚠️ **历史文档（已废弃）**：原文档定义 POST /api/v1/reviews/switch 用于提交评价，
> 实测后端未实现该 POST 接口（返回 1002 请求方法不允许）。
> 当前评价提交请改用 9.1 订单投票接口：
> POST /api/v1/orders/{orderId}/items/{productId}/vote
> Body: { "rating": "LIKE" | "DISLIKE" }
`

---

## 六、菜品搜索 /api/v1/search

> **#SEARCH-001 语义搜索**：支持分词匹配 + 语义扩展 + 分类/营养筛选

### 6.1 搜索菜品（ES 语义搜索）
`
GET /api/v1/search
Query 参数:
  query    String  搜索关键词，如"清淡的汤"、"番茄"（必填）
  category Long    分类ID（可选）
  page     int     页码（默认1）
  size     int     每页条数（默认20，最大50）

Response: R<SearchResponse>
{
  "code": 0,
  "data": {
    "products": [
      {
        "id": "1",
        "name": "番茄鱼汤",
        "description": "新鲜番茄配嫩滑鱼肉...",
        "cover": "http://...",
        "category": "汤品",
        "price": 40.00,
        "calories": 150,
        "protein": 22.5,
        "fat": 8.2,
        "available": true
      }
    ],
    "total": 9,
    "page": 1,
    "size": 20,
    "costMs": 125
  }
}
`

---

## 七、购物车 /api/v1/cart

> 需要 USER 角色认证

### 7.1 获取购物车
`
GET /api/v1/cart
Authorization: Bearer <token>

Response: R<CartVO>
{
  "code": 0,
  "data": {
    "items": [
      {
        "productId": "3",
        "name": "番茄鱼汤",
        "cover": "http://...",
        "price": 40.0,
        "quantity": 1,
        "subtotal": 40.0
      }
    ],
    "totalAmount": 40.0,
    "itemCount": 1
  }
}
`

### 7.2 添加商品
`
POST /api/v1/cart/items
Authorization: Bearer <token>
Content-Type: application/json

Body:
{
  "productId": 3,            // 商品ID（必填）
  "quantity": 1               // 数量（必填，默认1）
}

Response: R<Void>
`

### 7.3 更新数量
`
PUT /api/v1/cart/items/{productId}
Authorization: Bearer <token>
Content-Type: application/json

Body:
{
  "quantity": 2               // 新数量
}
`

### 7.4 删除商品
`
DELETE /api/v1/cart/items/{productId}
Authorization: Bearer <token>

Response: R<Void>
`

### 7.5 清空购物车
`
DELETE /api/v1/cart
Authorization: Bearer <token>

Response: R<Void>
`

---

## 八、用户订单 /api/v1/user/orders

> 需要 USER 角色认证

### 8.1 创建订单
`
POST /api/v1/user/orders
Authorization: Bearer <token>
Content-Type: application/json

Body:
{
  "items": [                  // 商品列表（必填，至少1项）
    {
      "productId": 3,         // 商品ID（必填）
      "quantity": 1           // 数量（必填）
    }
  ],
  "addressId": 4,             // 收货地址ID（二选一）
  "receiverName": "张三",      // 收货人姓名（二选一）
  "receiverPhone": "138...",
  "receiverAddress": "湖南省...",
  "remark": "少放盐"           // 备注（可选）
}

Response: R<OrderVO>
{
  "code": 0,
  "data": {
    "id": "2087100615569851303",
    "orderNo": "ORD202609101926420412",
    "totalAmount": 40.0,
    "status": 1,               // 1=待支付
    "statusDesc": "待支付",
    "receiverName": "张三",
    "receiverPhone": "13878789999",
    "receiverAddress": "湖南省衡阳市蒸湘区某街道123号",
    "items": [...],
    "createTime": "2026-09-10T19:26:42"
  }
}
`

### 8.2 订单列表
`
GET /api/v1/user/orders
Authorization: Bearer <token>

Query:
  status   Integer 订单状态筛选（1=待支付 2=已支付 3=配送中 4=已完成 5=已取消）（可选）
  page     int     页码（默认1）
  size     int     每页大小（默认10）

Response: R<Page<OrderVO>>
`

### 8.3 订单详情
`
GET /api/v1/user/orders/{id}
Authorization: Bearer <token>

Response: R<OrderVO>
`

### 8.4 按订单号查询
`
GET /api/v1/user/orders/by-order-no/{orderNo}
Authorization: Bearer <token>

Response: R<OrderVO>
`

### 8.5 取消订单
`
PUT /api/v1/user/orders/{id}/cancel
Authorization: Bearer <token>

Response: R<Void>
限制：仅 status=1（待支付）可取消
`

### 8.6 确认收货
`
PUT /api/v1/user/orders/{id}/confirm
Authorization: Bearer <token>

Response: R<Void>
限制：仅 status=3（配送中）可确认
`

---

## 九、订单投票 /api/v1/orders

### 9.1 对菜品投票
`
POST /api/v1/orders/{orderId}/items/{productId}/vote
Authorization: Bearer <token>
Content-Type: application/json

Body:
{
  "rating": "LIKE"           // LIKE 或 DISLIKE
}

Response: R<Void>
`

---

## 十、支付模块 /api/v1/payments

> 需要 USER 角色认证

### 10.1 发起支付
`
POST /api/v1/payments
Authorization: Bearer <token>
Content-Type: application/json

Body（普通支付）:
{
  "orderId": 2087100615569851303
}

Body（免单支付）:
{
  "orderId": 2087100615569851303,
  "freeOrderCode": "FREE20260910170556001"
}

Response: R<PaymentVO>
{
  "code": 0,
  "data": {
    "orderNo": "ORD202609101926420412",
    "payUrl": "https://openapi.alipay.com/...",
    "payForm": null,
    "freeOrder": false,        // true=免单
    "freeOrderCode": null,
    "paidAmount": 40.0,       // 免单时为0
    "status": "PENDING"
  }
}
`

### 10.2 查询支付状态
`
GET /api/v1/payments/{orderNo}
Authorization: Bearer <token>

Response: R<PaymentStatusVO>
⚠️【已修正】实测后端字段名/类型与本节原文档不符，实际：
{
  "code": 0,
  "data": {
    "orderNo": "ORD202609102200532778",
    "payNo": "FREE1789048856147",
    "status": 1,                 // ⚠️ 数字枚举：0=PENDING 1=SUCCESS 2=FAILED 3=CLOSED（非字符串）
    "amount": null,              // ⚠️ 字段名是 amount（不是 paidAmount）
    "paidAt": "2026-09-10T22:00:56",   // ⚠️ 字段名是 paidAt（不是 paidTime）
    "tradeNo": null,
    "buyerUserId": null,
    "buyerLogonId": null
  }
}
`

---

## 十一、免单活动 /api/v1/free-orders

> 需要 USER 角色认证

### 11.1 获取当前可抢活动
`
GET /api/v1/free-orders/active
Authorization: Bearer <token>

Response: R<List<FreeOrderActivityVO>>
{
  "code": 0,
  "data": [
    {
      "id": 4,
      "name": "立即可抢测试",
      "startTime": "2026-09-10T15:00:00",
      "grabTime": "2026-09-10T17:04:56",
      "endTime": "2026-09-11T15:00:00",
      "totalQuota": 5,
      "remainingQuota": 4,
      "status": "PUBLISHED"
    }
  ]
}
`

### 11.2 抢免单券
`
POST /api/v1/free-orders/{activityId}/grab
Authorization: Bearer <token>

Response: R<FreeOrderCouponVO>
成功 (200):
{
  "code": 0,
  "data": {
    "id": 12,
    "activityId": 4,
    "code": "FREE20260910170556001",
    "maxAmount": 30.00,
    "status": "GRABBED",
    "grabbedTime": "2026-09-10T17:05:56"
  }
}

失败 (200 + code != 0):
{
  "code": 9104,               // 已抢过
  "message": "您已抢过本活动免单"
}
{
  "code": 9105,               // 抢光了
  "message": "来晚一步，免单券已抢完"
}
{
  "code": 9101,               // 活动未开始
  "message": "活动尚未开始"
}
{
  "code": 9102,               // 活动已结束
  "message": "活动已结束"
}
`

### 11.3 我的免单券
`
GET /api/v1/free-orders/my-coupons
Authorization: Bearer <token>

Response: R<List<FreeOrderCouponVO>>
{
  "code": 0,
  "data": [
    {
      "id": 12,
      "activityId": 4,
      "code": "FREE20260910170556001",
      "maxAmount": 30.00,
      "status": "GRABBED",    // GRABBED=已抢待用  USED=已使用
      "grabbedTime": "2026-09-10T17:05:56"
    }
  ]
}
`

---

## 十二、站内消息 /api/v1/messages

> 需要 USER 角色认证

### 12.1 消息列表
`
GET /api/v1/messages
Authorization: Bearer <token>

Query:
  isRead   Integer 是否已读 0=未读 1=已读（可选）
  page     int     页码（默认1）
  size     int     每页大小（默认10）

Response: R<Page<MessageVO>>
`

### 12.2 未读数
`
GET /api/v1/messages/unread-count
Authorization: Bearer <token>

Response: R<Integer>
`

### 12.3 标记已读
`
PUT /api/v1/messages/{id}/read
Authorization: Bearer <token>

Response: R<Void>
`

### 12.4 全部已读
`
PUT /api/v1/messages/read-all
Authorization: Bearer <token>

Response: R<Void>
`

---

## 十三、文件上传 /api/v1/files

### 13.1 上传文件
`
POST /api/v1/files/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

Form 字段:
  file    MultipartFile  文件（必填）

Response: R<FileVO>
{
  "code": 0,
  "data": {
    "url": "http://127.0.0.1:9000/product-picture/xxx.jpg",
    "md5": "abc123..."
  }
}
`

### 13.2 秒传（MD5查重）
`
GET /api/v1/files/info?md5=abc123
Authorization: Bearer <token>

Response: R<FileVO>
存在: 返回 url
不存在: code=404
`

### 13.3 删除文件
`
DELETE /api/v1/files/{md5}
Authorization: Bearer <token>

Response: R<Void>
`

---

## 十四、智能客服 /api/v1/cs

> 需要 USER 角色认证；**SSE 流式接口**

### 14.1 发送消息
`
POST /api/v1/cs/chat
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

Body:
{
  "message": "番茄鱼汤卡路里多少？"    // 用户消息（必填）
}

响应: SSE 流（text/event-stream）
每个事件格式:
  data: {"type":"token","content":"番"}
  data: {"type":"token","content":"番茄"}
  ...
  data: {"type":"tool","content":"..."}   // 工具调用事件
  data: {"type":"done","content":""}      // 结束事件

前端解析示例:
  const eventSource = new EventSourcePolyfill(url, {
    headers: { Authorization: Bearer  }
  });
  eventSource.onmessage = (e) => {
    const data = JSON.parse(e.data);
    if (data.type === 'token') { appendText(data.content); }
    if (data.type === 'done')  { hideTyping(); }
    if (data.type === 'error') { showError(data.content); }
  };
`

### 14.2 健康检查
`
GET /api/v1/cs/health

Response: { "status": "UP", "service": "del-cs", "mode": "langchain4j" }
`

---

## 十五、管理员端 /api/v1/admin

> 需要 ADMIN 或 SUPER_ADMIN 角色认证

### 15.1 管理员登录
`
POST /api/v1/admin/auth/login
Content-Type: application/json

Body:
{
  "username": "admin",        // 或 superadmin
  "password": "123456"
}

Response: R<LoginResp>（同用户登录）
`

### 15.2 当前管理员信息
`
GET /api/v1/admin/auth/me
Authorization: Bearer <admin_token>

Response: R<LoginResp.userInfo>
`

### 15.3 管理员菜品管理
`
GET    /api/v1/admin/products          分页查询（Query: page,size,name,categoryId,status）
GET    /api/v1/admin/products/{id}    菜品详情
POST   /api/v1/admin/products          新增菜品（Body: ProductReq）
PUT    /api/v1/admin/products/{id}     更新菜品
DELETE /api/v1/admin/products/{id}     删除菜品
PUT    /api/v1/admin/products/{id}/status   更新状态（上架/下架）
PUT    /api/v1/admin/products/{id}/stock    更新库存
`

### 15.4 管理员分类管理
`
GET    /api/v1/admin/categories/all    所有分类（下拉框用）
GET    /api/v1/admin/categories       分页查询
POST   /api/v1/admin/categories        新增分类
PUT    /api/v1/admin/categories/{id}   更新分类
DELETE /api/v1/admin/categories/{id}   删除分类
`

### 15.5 管理员评价管理
`
PUT /api/v1/admin/reviews/praise    批量点赞（Body: {orderItemIds: []}）
PUT /api/v1/admin/reviews/bad      批量点踩（Body: {orderItemIds: []}）
GET /api/v1/admin/reviews/switch   评价开关查询
`

### 15.6 管理员订单管理
`
GET /api/v1/admin/orders           分页查询（Query: status,startDate,endDate,page,size）
GET /api/v1/admin/orders/{id}       订单详情
PUT /api/v1/admin/orders/{id}/status  修改订单状态（Body: {status: 3/4/5}）
`

### 15.7 管理员用户管理
`
GET /api/v1/admin/users             分页查询（Query: username,page,size）
GET /api/v1/admin/users/{id}        用户详情
PUT /api/v1/admin/users/{id}/status  禁用/启用用户
`

### 15.8 管理员地址管理
`
GET    /api/v1/admin/addresses       分页查询
DELETE /api/v1/admin/addresses/{id}  删除地址
`

### 15.9 管理员消息管理
`
GET /api/v1/admin/messages          消息列表（Query: userId,isRead,page,size）
`

### 15.10 管理员免单活动管理
`
POST   /api/v1/admin/free-orders              创建活动（Body: name,grabTime,endTime,totalQuota,maxFreeAmount）
POST   /api/v1/admin/free-orders/{id}/publish 发布活动
GET    /api/v1/admin/free-orders              活动列表
`

### 15.11 管理员统计
`
GET /api/v1/admin/stats/overview      运营概览（今日订单/销售额/用户数）
GET /api/v1/admin/stats/sales         销售趋势
GET /api/v1/admin/stats/hot-products  热销商品排行
`

### 15.12 搜索索引管理
`
POST /api/v1/admin/index/rebuild    重建ES索引（耗时操作）
GET  /api/v1/admin/index/status     索引重建状态
`

---

## 十六、通用响应结构

### 16.1 R<T> 包装
所有接口统一返回：
`json
{
  "code": 0,                // 0=成功，非0=失败
  "message": "ok",          // 状态描述
  "source": "del-product",   // 来源服务
  "data": { ... },          // 业务数据（成功时有）
  "timestamp": 1789039393   // 毫秒时间戳
}
`

### 16.2 错误码表

| 错误码 | 说明 | 适用场景 |
|------|------|---------|
| 0 | 成功 | 所有接口 |
| 1001 | 参数校验失败 | 请求体校验 |
| 1002 | 请求方法不允许 | HTTP方法错误 |
| 2001 | 用户未登录 | 需要认证的接口 |
| 2002 | 登录失败 | 用户名密码错误 |
| 2003 | Token无效 | JWT过期/伪造 |
| 3001 | 资源不存在 | ID不存在 |
| 4001 | 库存不足 | 下单时 |
| 5001 | 订单状态不允许操作 | 取消/确认等状态校验 |
| 9101 | 活动未开始 | 抢免单 |
| 9102 | 活动已结束 | 抢免单 |
| 9103 | 免单券不存在 | 使用免单券 |
| 9104 | 已抢过本活动免单 | 重复抢免单 |
| 9105 | 免单券已抢完 | 配额用尽 |
| 9106 | 免单券已使用 | 重复使用 |
| 9107 | 超出免单额度 | 订单金额>免单券额度 |
| 9999 | 系统异常 | 服务器内部错误 |

---

## 十七、前端注意事项

### 17.1 JWT Token 处理
- 登录成功后从 data.accessToken 取 token
- 存储在 localStorage，请求时放 Authorization: Bearer <token> 头
- ole 字段区分身份：USER/ADMIN/SUPER_ADMIN
- admin 用户登录走 /api/v1/admin/auth/login，C端用户走 /api/v1/auth/login

### 17.2 Long 类型精度
- 数据库 ID（雪花ID）返回给前端时为 **String 类型**（防JS精度丢失）
- 前端 :key 绑定应使用 p.id（String）而非数字索引

### 17.3 订单状态值
| status | 说明 |
|--------|------|
| 1 | 待支付 |
| 2 | 已支付 |
| 3 | 配送中 |
| 4 | 已完成 |
| 5 | 已取消 |

### 17.4 免单券状态值
| status | 说明 |
|--------|------|
| AVAILABLE | 未被抢（公海） |
| GRABBED | 已抢到，待使用 |
| USED | 已使用 |

### 17.5 SSE 断线重连
- SseEmitter 默认超时 60 秒
- 前端应监听 onerror 并在断线后延迟 3 秒重连
- 带上完整的对话上下文（聊天记忆由后端维护）
