# 微服务架构重构 - 迭代进度文档

---

## 更新时间：2026-09-01

---

## 迭代1：Outbox基础设施 - 已完成

- UserEventOutboxRelay / OrderOutboxRelay
- MQ拓扑: user.event.exchange, order.event.exchange

---

## 迭代2-4：业务迁移 - 已完成

| 服务 | 端口 | 数据库 |
|------|------|--------|
| del-user | 10003 | del_user_db |
| del-product | 10000 | del_product_db |
| del-order | 10001 | del_order_db |

---

## 迭代5：Feign统计接口 + del-stats 重构 - 已完成

- del-order/user/product 统计接口
- del-stats 聚合服务 (端口10009)

---

## 迭代6：消息消费服务（邮件发送）- 已完成

- LoginEventConsumer, RegisterEventConsumer
- OrderCreatedEventConsumer, OrderEventConsumer
- MQ队列: user.event.exchange, order.event.exchange, payment.order.paid.exchange

---

## 迭代7：配置修复与Nacos统一 - 已完成

- Nacos配置统一 (16个配置文件)
- FeignClient冲突修复 (8个contextId)
- pay_outbox表 已创建

---

## 迭代8：PV/UV统计功能 - 已完成

- 实现位置: del-stats 服务
- AccessLogInterceptor 自动采集
- API: /internal/stats/visit/today, /range

---

## 迭代9：商品点赞/踩功能 - 已完成

**实现位置**: del-product 服务

**新增组件**:
- VoteEvent, VoteProducer, VoteConsumer
- RabbitMQConfig (vote.exchange → vote.queue)

**核心功能**:
- 用户点赞/踩（基于订单）
- 三级缓存 (Caffeine+Redis+MySQL)
- MQ异步处理
- 管理开关

---

## 当前服务状态

| 服务 | 端口 | 状态 |
|------|------|------|
| del-gateway | 10010 | 正常 |
| del-user | 10003 | 正常 |
| del-admin | 10002 | 正常 |
| del-product | 10000 | 正常 |
| del-order | 10001 | 正常 |
| del-payment | 10004 | 正常 |
| del-message | 10005 | 正常 |
| del-stats | 10009 | 正常 |
| del-file | 10006 | 正常 |

---

## 内部API认证

X-Internal-Service-Token: internal-service-secret-key-2024

---

## 功能完成情况

| 功能 | 状态 |
|---------|------|
| 微服务拆分 | 已完成 |
| 用户/商品/订单/支付 | 已完成 |
| 消息中心+邮件通知 | 已完成 |
| 统计服务+Feign聚合 | 已完成 |
| 网关+管理后台 | 已完成 |
| Nacos配置统一 | 已完成 |
| Outbox模式 | 已完成 |
| PV/UV统计 | 已完成 |
| 商品点赞/踩 | 已完成 |
| 文件上传+Redis去重 | 已完成 |

---

## 后续可优化功能

| 功能 | 复杂度 | 说明 |
|------|--------|------|
| 定时任务服务(PDF/Excel) | 中 | 管理后台刚需 |
| 抢免单功能 | 高 | 业务复杂 |
| SSO(QQ/微信) | 高 | 需第三方平台 |
| ES语义搜索 | 高 | 需部署ES |
| 智能客服 | 高 | 需接入LangChain/Coze |

---

*文档更新于 2026-09-01*
