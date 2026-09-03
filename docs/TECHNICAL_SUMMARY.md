# 🚀 Delivery-Cloud 微服务平台 - 技术总结文档

**文档版本**：1.0  
**更新日期**：2026-08-31  
**项目状态**：✅ 所有微服务已上线运行

---

## 一、项目概述

### 1.1 项目简介

delivery-cloud 是一个基于 Spring Cloud Alibaba 的微服务外卖平台，采用多服务拆分架构，实现了用户、商品、订单、支付、消息通知等核心业务的功能解耦与独立部署。

### 1.2 技术栈

| 层级 | 技术选型 | 版本 |
|------|----------|------|
| 基础框架 | Spring Boot | 3.x |
| 微服务框架 | Spring Cloud Alibaba | 2023.x |
| 服务注册与配置 | Nacos | 2.x |
| 数据库 | MySQL | 8.x |
| 缓存 | Redis | - |
| 消息队列 | RabbitMQ | - |
| API 网关 | Spring Cloud Gateway | - |
| ORM | MyBatis Plus | 3.5.x |
| 远程调用 | OpenFeign | - |
| 支付宝 | Alipay SDK | - |

---

## 二、系统架构

### 2.1 服务拓扑

`
                          ┌─────────────────┐
                          │   Nacos Server  │
                          │  (服务注册/配置) │
                          └────────┬────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
    ┌─────────▼─────────┐ ┌──────▼──────┐ ┌──────────▼──────────┐
    │    del-gateway     │ │  del-admin  │ │     del-stats       │
    │   (API Gateway)    │ │  (管理后台)  │ │   (统计聚合服务)    │
    │     Port: 10008   │ │  Port: 10005│ │    Port: 10006     │
    └─────────┬─────────┘ └─────────────┘ └──────────┬──────────┘
              │                                       │
              │              ┌─────────────────────────┤
              │              │                         │
    ┌─────────▼─────────┐ ┌─▼─────────┐ ┌──────────▼──────────┐
    │    del-user       │ │ del-order │ │    del-product      │
    │    (用户服务)     │ │  (订单服务) │ │    (商品服务)       │
    │   Port: 10003     │ │  Port: 10001│ │    Port: 10000     │
    └─────────┬─────────┘ └─────┬─────┘ └──────────┬──────────┘
              │                 │                   │
              │        ┌────────┴────────┐          │
              │        │                 │          │
    ┌─────────▼─────────▼─┐ ┌──────────▼──────────┐ │
    │    del-message     │ │    del-payment     │ │
    │   (消息中心)        │ │    (支付服务)       │ │
    │   Port: 10002     │ │    Port: 10004     │ │
    └────────────────────┘ └───────────────────┘ │
                                                  │
                          ┌───────────────────────┘
                   ┌──────▼──────┐
                   │   RabbitMQ  │
                   │   (消息队列) │
                   └─────────────┘
`

### 2.2 服务端口映射

| 服务 | 端口 | 数据库 | 缓存 | MQ |
|------|------|--------|------|-----|
| del-gateway | 10008 | - | - | - |
| del-admin | 10005 | del_admin_db | Redis | - |
| del-user | 10003 | del_user_db | Redis | RabbitMQ |
| del-product | 10000 | del_product_db | Redis | - |
| del-order | 10001 | del_order_db | Redis | RabbitMQ |
| del-message | 10002 | del_message_db | - | RabbitMQ |
| del-payment | 10004 | del_payment_db | Redis | RabbitMQ |
| del-stats | 10006 | del_stats_db | - | - |

---

## 三、微服务详情

### 3.1 del-gateway（API 网关）

| 属性 | 说明 |
|------|------|
| **端口** | 10008 |
| **职责** | 统一入口、路由转发、JWT 鉴权、内部服务 Token 验证 |
| **核心组件** | JwtVerifier、AuthGlobalFilter、PathRoleRule |
| **路由规则** | C 端 /api/v1/* → 各业务服务；B 端 /api/v1/admin/* → del-admin |

### 3.2 del-user（用户服务）

| 属性 | 说明 |
|------|------|
| **端口** | 10003 |
| **职责** | 用户注册/登录、地址管理 |
| **数据库表** | t_user, t_user_address, user_event_outbox |
| **Outbox** | UserEventOutboxRelay（处理 REGISTER/LOGIN 事件） |
| **MQ 拓扑** | user.event.exchange (fanout) → del-message.user.event |

### 3.3 del-product（商品服务）

| 属性 | 说明 |
|------|------|
| **端口** | 10000 |
| **职责** | 商品管理、分类管理、商品评价 |
| **数据库表** | t_product, t_category, t_review |

### 3.4 del-order（订单服务）

| 属性 | 说明 |
|------|------|
| **端口** | 10001 |
| **职责** | 订单管理、购物车管理 |
| **数据库表** | t_order, t_order_item, t_cart, order_outbox |
| **Outbox** | OrderOutboxRelay（处理 ORDER_CREATED 事件） |
| **MQ 拓扑** | order.event.exchange (fanout) → del-message.order.event |

### 3.5 del-payment（支付服务）

| 属性 | 说明 |
|------|------|
| **端口** | 10004 |
| **职责** | 支付宝支付、退款、对账 |
| **数据库表** | t_payment |
| **Outbox** | PayOutboxRelay（处理 ORDER_PAID 事件） |
| **MQ 拓扑** | payment.order.paid.exchange (fanout) → del-message.order.paid |

### 3.6 del-message（消息中心）

| 属性 | 说明 |
|------|------|
| **端口** | 10002 |
| **职责** | 邮件发送、站内通知 |
| **数据库表** | - |
| **消费者** | LoginEventConsumer、RegisterEventConsumer、OrderCreatedEventConsumer、OrderEventConsumer |
| **邮件模板** | login-success.html、register-success.html、order-created.html、payment-success.html |

### 3.7 del-stats（统计服务）

| 属性 | 说明 |
|------|------|
| **端口** | 10006 |
| **职责** | 数据统计聚合（通过 Feign 调用各服务） |
| **数据库表** | - |
| **Feign 客户端** | OrderStatsClient、UserStatsClient、ProductStatsClient |

### 3.8 del-admin（管理后台）

| 属性 | 说明 |
|------|------|
| **端口** | 10005 |
| **职责** | 后台管理功能 |

---

## 四、内部 API

| 端点 | 方法 | 服务 | 说明 |
|------|------|------|------|
| /internal/stats/today | GET | del-order | 今日订单统计 |
| /internal/stats/sales-trend | GET | del-order | 销售趋势 |
| /internal/stats/users | GET | del-user | 用户统计 |
| /internal/stats/hot-products | GET | del-product | 热卖商品 |

**认证方式**：Header X-Internal-Service-Token: internal-service-secret-key-2024

---

## 五、Outbox 模式

### 5.1 概述

采用 Outbox 模式解决本地事务与 MQ 投递的原子性问题。

### 5.2 状态码

| 状态码 | 名称 | 说明 |
|--------|------|------|
| 0 | NEW | 待投递 |
| 1 | SENT | 已投递到 MQ |
| 2 | ACK | 消费方已确认 |
| 3 | DLQ | 进入死信队列 |

### 5.3 实现的 Outbox

| 表名 | 服务 | 事件类型 |
|------|------|----------|
| user_event_outbox | del-user | REGISTER, LOGIN |
| order_outbox | del-order | ORDER_CREATED |
| pay_outbox | del-payment | ORDER_PAID |

---

## 六、Nacos 配置

### 6.1 配置文件清单

| 配置文件 | 用途 |
|----------|------|
| common-jwt.yml | JWT 密钥（共享） |
| common-actuator.yml | Actuator 端点 |
| common-amqp.yml | RabbitMQ 连接 |
| common-redis.yml | Redis 连接 |
| common-mybatis.yml | MyBatis Plus 配置 |
| common-jackson.yml | Jackson 配置 |
| common-swagger.yml | Swagger 文档 |
| alipay.yml | 支付宝配置 |
| del-*.yml | 各服务独立配置 |

### 6.2 敏感信息管理

| 变量 | 用途 | 默认值 |
|------|------|--------|
| ${DB_PASSWORD} | MySQL 密码 | sakana013 |
| ${MAIL_USERNAME} | 邮件账号 | sakanaovo_13@163.com |
| ${MAIL_PASSWORD} | 邮件密码 | - |
| ${ALIPAY_APP_ID} | 支付宝应用 ID | 9021000166665620 |
| ${ALIPAY_NOTIFY_URL} | 回调地址 | - |
| ${RABBITMQ_PASSWORD} | RabbitMQ 密码 | 123456 |
| ${REDIS_PASSWORD} | Redis 密码 | 123456 |

---

## 七、已完成迭代

| 迭代 | 内容 | 状态 |
|------|------|------|
| 迭代1 | Outbox 基础设施 | ✅ |
| 迭代2 | del-user 业务迁移 | ✅ |
| 迭代3 | del-product 业务迁移 | ✅ |
| 迭代4 | del-order 业务迁移 | ✅ |
| 迭代5 | Feign 统计接口 + del-stats 重构 | ✅ |
| 迭代6 | del-message 邮件消费者 + 模板 | ✅ |
| 迭代7 | del-gateway 路由接入 + 安全修复 | ✅ |

---

## 八、修复记录

### 8.1 Bean 冲突修复

| 问题 | 解决方案 | 影响文件 |
|------|----------|----------|
| RedisTemplate Bean 名称冲突 | 重命名为 cartRedisTemplate | CartRedisConfig.java, CartServiceImpl.java |
| IdGeneratorApi 无效 @Configuration | 移除注解 | IdGeneratorApi.java |
| del-product 无用 @EnableFeignClients | 移除注解 | ProductApplication.java |
| FeignClient contextId 重复 | 为所有 FeignClient 添加唯一 contextId | 4 个 Feign Client |

### 8.2 配置缺失修复

| 服务 | 问题 | 修复 |
|------|------|------|
| del-payment | 缺少 common-redis.yml, common-amqp.yml | 添加 import |
| del-payment | pay_outbox 表不存在 | 需手动创建 |

---

## 九、当前状态

| 检查项 | 状态 |
|--------|------|
| 所有服务启动 | ✅ 正常 |
| Nacos 注册 | ✅ 正常 |
| 数据库连接 | ✅ 正常 |
| RabbitMQ 连接 | ✅ 正常 |
| Redis 连接 | ✅ 正常 |
| JWT 鉴权 | ✅ 正常 |
| Feign 调用 | ✅ 正常 |

---

## 十、待处理事项

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P0 | pay_outbox 建表 | 建表 SQL 已提供，需在 MySQL 执行 |
| P1 | Feign Client contextId | 建议为所有 FeignClient 添加唯一 contextId |
| P2 | 密码环境变量化 | RabbitMQ/Redis 密码硬编码，建议改为环境变量 |

---

## 十一、文件结构

`
delivery-cloud/
├── del-admin/              # 管理后台
├── del-common/             # 公共模块（Feign Client、实体等）
├── del-gateway/            # API 网关
├── del-message/            # 消息中心
├── del-order/              # 订单服务
├── del-payment/            # 支付服务
├── del-product/            # 商品服务
├── del-stats/              # 统计服务
├── del-user/               # 用户服务
├── scripts/                # 数据库脚本
│   ├── outbox_tables.sql
│   └── business_tables.sql
├── nacos-config/           # Nacos 配置
└── docs/
    └── TECHNICAL_SUMMARY.md
`

---

**文档结束**
