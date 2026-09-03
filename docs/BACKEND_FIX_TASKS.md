# 后端修复任务清单

**来源**：前后端联调第二轮验收
**报告日期**：2026-09-01
**责任人**：后端工程师 / 前端工程师

---

## 🚨 阻塞性任务（P0 - 必须修复）

### 任务 #1：网关路由冲突 - 订单接口被错误路由

**严重程度**：🔴 阻塞
**影响范围**：所有订单操作（下单/查询/详情/取消/确认收货）
**责任方**：后端

#### 问题描述

Spring Cloud Gateway 按 routes 列表顺序匹配规则，第一个匹配的路由胜出。当前 del-user 路由的 Path 包含 /api/v1/user/**，会优先匹配 /api/v1/user/orders 请求，导致请求被错误路由到 del-user，而 OrderController 实际在 del-order 服务，**所有订单操作会返回 404**。

#### 修复文件

E:\Idea_project\delivery-cloud\nacos-config\nacos_config_export_20260901135535\DEFAULT_GROUP\del-gateway.yml

#### 最终修复后配置

`yaml
- id: del-user
  uri: lb://del-user
  predicates:
    - Path=/api/v1/auth/**,/api/v1/user/addresses/**
  filters:
    - StripPrefix=0

- id: del-order
  uri: lb://del-order
  predicates:
    - Path=/api/v1/orders/**,/api/v1/cart/**,/api/v1/user/orders/**
  filters:
    - StripPrefix=0
`

#### 验证方法

`ash
curl -X POST http://localhost:10010/api/v1/user/orders \\
  -H \"Authorization: Bearer <token>\" \\
  -H \"Content-Type: application/json\" \\
  -d '{\"addressId\":\"123\",\"paymentMethod\":1,\"items\":[{\"productId\":\"456\",\"quantity\":1}]}'

# 预期：返回订单信息（不是 404）
`

---

### 任务 #2：网关路由缺失 - 评价开关接口

**严重程度**：🔴 阻塞
**影响范围**：商品评价开关功能（点赞/点踩）
**责任方**：后端

#### 问题描述

前端 praise.ts 调用 /api/v1/reviews/switch 获取评价开关状态，但当前 del-gateway.yml 中没有任何路由匹配 /api/v1/reviews/**，请求会直接 404。

#### 最终修复后配置

`yaml
- id: del-product
  uri: lb://del-product
  predicates:
    - Path=/api/v1/products/**,/api/v1/categories/**,/api/v1/reviews/**,/api/v1/orders/*/items/*/vote
  filters:
    - StripPrefix=0
`

#### 验证方法

`ash
curl -X GET http://localhost:10010/api/v1/reviews/switch
# 预期：返回评价开关状态（不是 404）
`

---

## ⚠️ 后续优化任务（P1 - 建议完成）

### 任务 #3：前端运行 
pm run build 验证 TypeScript

**严重程度**：🟡 警告
**影响范围**：前端代码完整性
**责任方**：前端

#### 说明

报告声称 
pm run build 通过，但未提供独立验证证据。前端工程师需要运行以下命令验证：

`ash
cd E:\VSCode_workspace\Delivery
npm run build
`

#### 预期输出

`
✓ built in 9.0s
`

#### 验证清单

- [ ] ue-tsc --noEmit 通过（0 类型错误）
- [ ] ite build 成功生成 dist 目录
- [ ] 无 TypeScript 编译错误
- [ ] 无 ESLint 警告（如果有）

---

### 任务 #4：DevTools 抓包验证实际请求链路

**严重程度**：🟡 警告
**影响范围**：联调准确性
**责任方**：联调（前端主导，后端配合）

#### 说明

仅配置文件层面验证通过不代表实际可用。需要通过 DevTools Network 面板抓包，验证前端实际发出的请求是否正确路由到后端并返回正确响应。

#### 验证工具

参考文档：E:\Idea_project\delivery-cloud\docs\DEVTOOLS_VERIFICATION_GUIDE.md

#### 验证步骤摘要

1. 启动前端 dev server (
pm run dev)
2. 打开 Chrome DevTools → Network 面板
3. 勾选 Preserve log
4. 执行以下操作，每个操作记录 Network 请求：
   - 登录
   - 查看商品列表
   - 查看商品详情
   - 加购物车
   - 查看购物车
   - **创建订单**（最关键）
   - 取消订单
   - 支付订单
   - 点赞商品

#### 关键检查点

- [ ] 所有请求 URL 端口为 10010
- [ ] 所有路径以 /api/v1/ 开头
- [ ] 创建订单请求体包含 ddressId、paymentMethod、items[]
- [ ] 订单请求路径为 /api/v1/user/orders（不是 /api/v1/orders）
- [ ] 点赞请求路径为 /api/v1/orders/{id}/items/{productId}/vote（不是 /user/orders/...）
- [ ] 所有 200 响应的业务码 code = 0

#### 提交产出

填写 DEVTOOLS_VERIFICATION_GUIDE.md 中的验证报告模板。

---

## ✅ 验收检查清单

修复后请逐项验证：

- [ ] 创建订单接口返回订单信息（不是 404）
- [ ] 订单列表接口返回订单列表（不是 404）
- [ ] 订单详情接口返回订单详情（不是 404）
- [ ] 取消订单接口成功（不是 404）
- [ ] 确认收货接口成功（不是 404）
- [ ] 评价开关接口返回开关状态（不是 404）
- [ ] 前端 
pm run build 通过
- [ ] DevTools 抓包验证所有接口通过

---

## 📋 任务汇总

| 任务 | 文件/工具 | 优先级 | 责任方 | 预计耗时 |
|------|----------|--------|--------|----------|
| #1 订单路由冲突 | del-gateway.yml | 🔴 P0 | 后端 | 5 分钟 |
| #2 评价路由缺失 | del-gateway.yml | 🔴 P0 | 后端 | 3 分钟 |
| #3 前端 build 验证 | npm run build | 🟡 P1 | 前端 | 5 分钟 |
| #4 DevTools 抓包验证 | Chrome DevTools | 🟡 P1 | 联调 | 30 分钟 |

---

## 📊 当前完成状态

- [x] #1 订单路由冲突 — 已修复 ✅
- [x] #2 评价路由缺失 — 已修复 ✅
- [ ] #3 前端 build 验证 — 待执行
- [ ] #4 DevTools 抓包验证 — 待执行

---

**P0 任务已完成，P1 任务待执行。完成后请通知联调负责人。**

*任务清单由 Codex 验收评审生成*
