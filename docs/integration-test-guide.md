# 集成测试指南 - delivery-cloud 微服务系统

**版本**: v1.0  
**日期**: 2026-08-30  
**测试阶段**: 迭代1-7 集成验证

---

## 1. 系统架构概览

```
客户端/前端
    │
    ▼
del-gateway (Port 10010)
    │ JWT 双密钥鉴权、路由转发、内部服务Token验证
    ▼
├── del-user (10003) ──→ del_user_db
├── del-product (10000) ──→ del_product_db
├── del-order (10001) ──→ del_order_db ──→ 发布MQ事件
├── del-admin (10002) ──→ del_admin_db
├── del-payment (10004) ──→ del_payment_db
├── del-message (10005) ──→ 消费MQ事件、发送邮件
└── del-stats ──→ Feign调用各服务内部API
```

## 2. 服务端口映射

| 服务 | 端口 | 数据库 | 用途 |
|------|------|--------|------|
| del-gateway | 10010 | - | API网关 |
| del-user | 10003 | del_user_db | 用户服务 |
| del-product | 10000 | del_product_db | 商品服务 |
| del-order | 10001 | del_order_db | 订单服务 |
| del-admin | 10002 | del_admin_db | 管理后台 |
| del-payment | 10004 | del_payment_db | 支付服务 |
| del-message | 10005 | del_message_db | 消息服务 |
| del-stats | 待启动 | - | 统计聚合 |

## 3. 前置条件

### 3.1 基础设施
- Nacos: 127.0.0.1:8848 (namespace: sakana)
- MySQL: 127.0.0.1:3306
- RabbitMQ: 127.0.0.1:5672 / 15672
- Redis: 127.0.0.1:6379

### 3.2 环境变量
| 变量 | 示例值 |
|------|--------|
| JWT_USER_POOL_SECRET | your-user-pool-secret-key-at-least-32-chars |
| JWT_ADMIN_POOL_SECRET | your-admin-pool-secret-key-at-least-32-chars |
| NACOS_SERVER_ADDR | 127.0.0.1:8848 |

### 3.3 启动顺序
1. 基础设施 (Nacos, MySQL, RabbitMQ, Redis)
2. del-user
3. del-product
4. del-order
5. del-admin
6. del-payment
7. del-message
8. del-stats
9. del-gateway

## 4. 核心业务流程测试用例

### UC-001: 用户注册→登录→获取用户信息
```
1. POST /api/v1/auth/register
   Body: {"username": "testuser", "password": "Test@123", "email": "test@example.com"}
   预期: 200, code=0, 发送MQ事件

2. POST /api/v1/auth/login
   Body: {"username": "testuser", "password": "Test@123"}
   预期: 200, code=0, JWT Token (source=USER_POOL)

3. GET /api/v1/user/profile
   Header: Authorization: Bearer <token>
   预期: 200, 用户信息
```

### UC-002: 商品浏览→加入购物车→创建订单
```
1. GET /api/v1/products              # 公开
2. GET /api/v1/products/{id}        # 公开
3. POST /api/v1/cart/items           # 需要USER Token
   Body: {"productId": 1001, "quantity": 2}
4. GET /api/v1/cart                  # 需要USER Token
5. POST /api/v1/orders               # 需要USER Token
   Body: {"addressId": 1, "paymentMethod": "ALIPAY"}
6. GET /api/v1/orders/{orderId}      # 需要USER Token
```

### UC-003: 管理员商品上架
```
1. POST /api/v1/admin/auth/login
   Body: {"username": "admin", "password": "Admin@123"}
   预期: JWT Token (source=ADMIN_POOL)

2. POST /api/v1/admin/products       # 需要ADMIN Token
   Body: {"name": "测试商品", "price": 99.99, "categoryId": 1, "stock": 100}

3. GET /api/v1/products/{productId}  # 公开
```

### UC-004: 订单支付流程
```
1. POST /api/v1/orders               # 创建订单
2. POST /api/v1/pay/create            # 创建支付
3. 模拟支付回调 POST /api/v1/pay/notify
4. GET /api/v1/orders/{orderId}       # 验证status=PAID
```

### UC-005: 统计API聚合查询
```
1. GET /internal/stats/today
   Header: X-Internal-Service-Token: internal-service-secret-key-2024
   预期: 今日订单数、销售额

2. GET /internal/stats/sales-trend?granularity=day&startDate=2024-01-01&endDate=2024-01-31

3. GET /internal/stats/users
   预期: 累计用户数、今日新用户

4. GET /internal/stats/hot-products?limit=10
   预期: 热卖商品TopN
```

## 5. API接口测试矩阵

### del-user 服务
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | /api/v1/auth/register | 公开 | 用户注册 |
| POST | /api/v1/auth/login | 公开 | 用户登录 |
| GET | /api/v1/user/profile | USER | 用户信息 |
| GET | /api/v1/addresses | USER | 地址列表 |
| POST | /api/v1/addresses | USER | 新增地址 |
| GET | /internal/stats/users | Internal | 用户统计 |

### del-product 服务
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | /api/v1/products | 公开 | 商品列表 |
| GET | /api/v1/products/{id} | 公开 | 商品详情 |
| GET | /api/v1/categories | 公开 | 分类列表 |
| POST | /api/v1/admin/products | ADMIN | 商品上架 |
| GET | /internal/stats/hot-products | Internal | 热卖统计 |

### del-order 服务
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | /api/v1/orders | USER | 订单列表 |
| POST | /api/v1/orders | USER | 创建订单 |
| GET | /api/v1/cart | USER | 购物车 |
| POST | /api/v1/cart/items | USER | 加购物车 |
| GET | /internal/stats/today | Internal | 今日统计 |
| GET | /internal/stats/sales-trend | Internal | 销售趋势 |

### del-admin 服务
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | /api/v1/admin/auth/login | 公开 | 管理员登录 |
| GET | /api/v1/admin/products | ADMIN | 商品管理 |
| POST | /api/v1/admin/products | ADMIN | 商品上架 |
| GET | /api/v1/admin/orders | ADMIN | 订单管理 |

### del-gateway 路由验证
| 源路径 | 目标服务 | 鉴权 |
|--------|----------|------|
| /api/v1/auth/** | del-user | 公开 |
| /api/v1/user/** | del-user | USER |
| /api/v1/products/** | del-product | 公开 |
| /api/v1/orders/** | del-order | USER |
| /api/v1/cart/** | del-order | USER |
| /api/v1/admin/** | del-admin | ADMIN |
| /internal/** | 各服务 | Internal Token |

## 6. MQ拓扑与事件流测试

### 6.1 RabbitMQ拓扑
```
user.event.exchange (fanout)
    └── del-message.user.event
            ├── LoginEventConsumer
            └── RegisterEventConsumer

order.event.exchange (fanout)
    └── del-message.order.event
            └── OrderCreatedEventConsumer

payment.order.paid.exchange (fanout)
    └── del-message.order.paid
            └── OrderEventConsumer (onOrderPaid)
```

### 6.2 事件消费验证
| 事件 | 生产者 | 消费者 | 验证点 |
|------|--------|--------|--------|
| 用户登录事件 | del-user | del-message | 邮件发送 |
| 用户注册事件 | del-user | del-message | 邮件发送、Outbox |
| 订单创建事件 | del-order | del-message | 邮件发送、Outbox |
| 订单支付事件 | del-payment | del-message | 邮件发送 |

### 6.3 Outbox可靠性测试
1. 创建订单，验证outbox记录status=NEW
2. 停止RabbitMQ
3. 重启del-order
4. 重启RabbitMQ
5. 验证Outbox: NEW→SENT→ACK

## 7. 安全与鉴权测试

### SEC-001: JWT双密钥隔离
```
1. C端登录 → Token.source=USER_POOL → 可访问/api/v1/user/**
2. B端登录 → Token.source=ADMIN_POOL → 可访问/api/v1/admin/**
3. USER_TOKEN访问admin接口 → 403
4. ADMIN_TOKEN访问user接口 → 403
```

### SEC-002: 内部API鉴权
```
1. GET /internal/stats/users (无Token) → 401
2. GET /internal/stats/users (X-Internal-Service-Token: wrong) → 403
3. GET /internal/stats/users (X-Internal-Service-Token: internal-service-secret-key-2024) → 200
```

### SEC-003: Token安全
```
1. 伪造Token → 401
2. 过期Token → 401
3. 格式错误 → 401
4. 缺少Authorization头 → 401
```

### 路径鉴权矩阵
| 路径 | 无Token | USER | ADMIN |
|------|---------|------|-------|
| /api/v1/auth/login | 200 | 200 | 200 |
| /api/v1/user/profile | 401 | 200 | 403 |
| /api/v1/admin/products | 401 | 403 | 200 |
| /api/v1/products | 200 | 200 | 200 |

## 8. 网关路由测试

### GW-001: 路由转发
```
GET /api/v1/products → del-product
GET /api/v1/user/profile → del-user
GET /api/v1/orders → del-order
GET /api/v1/admin/products → del-admin
```

### GW-002: 用户信息透传
```
使用USER Token访问 → 验证下游收到:
X-User-Id: <userId>
X-User-Role: USER
X-Token-Source: USER_POOL
```

## 9. 异常场景测试

### ERR-001: 下游服务宕机
1. 停止del-user
2. 访问/api/v1/user/profile
3. 验证返回有意义的错误
4. 重启del-user
5. 验证恢复

### ERR-002: MQ不可用
1. 停止RabbitMQ
2. 执行注册/登录
3. 验证主业务成功(HTTP 200)
4. 验证Outbox status=NEW
5. 重启RabbitMQ
6. 验证Outbox补发

## 10. 测试数据

### 测试账号
| 角色 | 用户名 | 密码 | Token Pool |
|------|--------|------|------------|
| 普通用户 | testuser | Test@123 | USER_POOL |
| 管理员 | admin | Admin@123 | ADMIN_POOL |
| 超级管理员 | superadmin | Super@123 | ADMIN_POOL |

### 内部服务调用
- Header: X-Internal-Service-Token
- Value: internal-service-secret-key-2024

## 11. 测试报告模板

```
### 测试执行记录
| 用例ID | 测试日期 | 执行人 | 结果 | 备注 |
|--------|----------|--------|------|------|

### 缺陷记录
| 缺陷ID | 严重程度 | 描述 | 截图 | 状态 |
|--------|----------|------|------|------|
```

---

**文档版本**: v1.0  
**编写日期**: 2026-08-30
