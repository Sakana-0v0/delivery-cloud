# DevTools 抓包验证指导 - 前端工程师

---

## 🎯 验证目标

确认前端 → 网关 → 后端的完整调用链路是否正确，解决"理论可行≠实际可用"的最后一公里问题。

---

## 📋 第一步：环境准备

### 1.1 启动服务

确认以下服务已启动并注册到 Nacos：

| 服务 | 端口 | 验证方法 |
|------|------|----------|
| del-gateway | 10010 | 浏览器访问 http://localhost:10010/actuator/health 返回 {\"status\":\"UP\"} |
| del-user | 10003 | Nacos 控制台可见 |
| del-order | 10001 | Nacos 控制台可见 |
| del-product | 10000 | Nacos 控制台可见 |
| del-payment | 10004 | Nacos 控制台可见 |
| del-message | 10005 | Nacos 控制台可见 |

### 1.2 前端启动

`ash
cd E:\VSCode_workspace\Delivery
npm run dev
`

确认浏览器可访问 http://localhost:5173。

### 1.3 打开 DevTools

- 按 F12 打开 Chrome DevTools
- 切换到 Network 面板
- 勾选 Preserve log（保留日志，刷新页面不清空）
- 清空现有日志（点 🚫 清空按钮）

---

## 📋 第二步：核心接口验证清单

### 🔴 验证批次 1：C端核心流程（必须全部通过）

| # | 操作 | 验证点 |
|---|------|--------|
| 1 | 访问登录页 | 触发 /auth/send-code（如果点击发送验证码） |
| 2 | 发送验证码 | /api/v1/auth/send-code |
| 3 | 注册新账号 | /api/v1/auth/register |
| 4 | 登录 | /api/v1/auth/login |
| 5 | 查看首页商品 | /api/v1/products、/api/v1/categories |
| 6 | 查看商品详情 | /api/v1/products/{id} |
| 7 | 加购 | /api/v1/cart/items |
| 8 | 查看购物车 | /api/v1/cart |
| 9 | 进入结算页 | /api/v1/user/addresses（自动加载地址） |
| 10 | 提交订单 | /api/v1/user/orders |
| 11 | 查看订单列表 | /api/v1/user/orders |
| 12 | 查看订单详情 | /api/v1/user/orders/{id} |
| 13 | 取消订单 | /api/v1/user/orders/{id}/cancel |
| 14 | 支付订单 | /api/v1/payments |
| 15 | 商品点赞 | /api/v1/orders/{id}/items/{productId}/vote |

### 🟡 验证批次 2：评价与消息

| # | 操作 | 验证点 |
|---|------|--------|
| 16 | 查看评价开关 | /api/v1/reviews/switch |
| 17 | 进入消息中心 | /api/v1/messages |
| 18 | 标记已读 | /api/v1/messages/{id}/read |

---

## 📋 第三步：每个接口的验证步骤

### 3.1 通用验证步骤（每个接口都要执行）

**步骤 A：请求检查**

1. 在 Network 面板找到对应请求（点击请求查看详情）
2. **General** 区域确认：
   - Request URL 是否正确？（应匹配预期 URL）
   - Request Method 是否正确？（GET/POST/PUT/DELETE）
3. **Request Headers** 区域确认：
   - Authorization: Bearer <token> 是否存在（需要鉴权的接口）
   - Content-Type: application/json 是否正确

**步骤 B：响应检查**

1. **Response Headers** 区域确认：
   - HTTP 状态码 = 200（或 201）
2. **Response** 区域（Preview/Response）确认：
   - code 字段 = 0（业务成功）
   - data 字段返回符合接口文档

**步骤 C：链路追溯**

如果出现 404/502/503 错误，记录：

- 完整 Request URL
- HTTP 状态码
- Response Body（如果有）

---

### 3.2 重点接口详细验证

#### 🔍 接口 #1：登录 /api/v1/auth/login

**操作**：在登录页输入用户名密码，点击登录

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/auth/login
Request Method: POST
Status Code:    200
Request Body:   {\"username\":\"testuser\",\"password\":\"Test@123\"}
Response Body:  {\"code\":0,\"data\":{\"accessToken\":\"...\",\"refreshToken\":\"...\",\"userInfo\":{...}}}
`

**重点检查**：
- [ ] 端口是 10010，不是 9000
- [ ] 路径是 /api/v1/auth/login，不是 /auth/login
- [ ] 响应包含 ccessToken
- [ ] 登录成功后 localStorage 出现 c_accessToken

#### 🔍 接口 #4：创建订单 /api/v1/user/orders（最关键）

**操作**：在结算页选择地址、支付方式，点击提交订单

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/user/orders
Request Method: POST
Status Code:    200
Request Body:   {
  \"addressId\": \"123\",
  \"paymentMethod\": 1,
  \"items\": [{\"productId\":\"456\",\"quantity\":1}],
  \"remark\": \"\"
}
Response Body:  {\"code\":0,\"data\":{...订单信息...}}
`

**重点检查**：
- [ ] 路径是 /api/v1/user/orders
- [ ] 请求体包含 ddressId、paymentMethod、items[]
- [ ] **不包含** eceiverName、eceiverPhone、eceiverAddress（已废弃字段）
- [ ] 响应 data.id 是字符串类型（雪花ID）

#### 🔍 接口 #5：订单列表 /api/v1/user/orders

**操作**：进入\"我的订单\"页面

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/user/orders?page=1&size=10
Request Method: GET
Status Code:    200
Response Body:  {\"code\":0,\"data\":{\"total\":1,\"records\":[...]}}
`

#### 🔍 接口 #11：点赞/踩 /api/v1/orders/{orderId}/items/{productId}/vote

**操作**：在订单详情页点击\"点赞\"按钮

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/orders/123/items/456/vote
Request Method: POST
Status Code:    200
Request Body:   {\"type\":\"like\"}
Response Body:  {\"code\":0,\"data\":{...投票结果...}}
`

**重点检查**：
- [ ] 路径是 /api/v1/orders/{id}/items/{productId}/vote
- [ ] **不是** /api/v1/user/orders/{id}/items/{productId}/vote

#### 🔍 接口 #12：地址列表 /api/v1/user/addresses

**操作**：进入结算页或地址管理页

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/user/addresses
Request Method: GET
Status Code:    200
Response Body:  {\"code\":0,\"data\":[...]}
`

#### 🔍 接口 #14：评价开关 /api/v1/reviews/switch

**操作**：在商品详情页（涉及评价时）

**预期 Network 请求**：
`
Request URL:    http://localhost:10010/api/v1/reviews/switch
Request Method: GET
Status Code:    200
Response Body:  {\"code\":0,\"data\":{\"praiseOpen\":1,\"badOpen\":1}}
`

---

## 📋 第四步：异常情况排查

### 🔴 错误 1：404 Not Found

**现象**：请求返回 404

**排查步骤**：
1. 确认 Request URL 完整且正确
2. 在 Nacos 控制台检查 del-gateway.yml 配置
3. 用以下命令测试网关路由：

`ash
# 测试网关可达性
curl -X POST http://localhost:10010/api/v1/auth/send-code \\
  -H \"Content-Type: application/json\" \\
  -d '{\"email\":\"test@test.com\"}'
`

4. 如果 curl 也 404 → 网关路由配置问题（联系后端）
5. 如果 curl 返回 200 → 前端代码问题（检查 axios 配置）

### 🔴 错误 2：CORS 跨域错误

**现象**：Network 中显示 (blocked:cors) 或请求被拦截

**排查步骤**：
1. 检查 vite.config.ts 是否配置 proxy
2. 如果没有 proxy，确认网关是否配置了 CORS 允许
3. 临时方案：在 vite.config.ts 中添加 proxy：

`	ypescript
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:10010',
        changeOrigin: true,
      },
    },
  },
})
`

### 🔴 错误 3：401 Unauthorized

**现象**：请求返回 401

**排查步骤**：
1. 检查 Authorization 头是否存在
2. 检查 Token 是否过期（查看 c_accessToken 是否在 localStorage 中）
3. 检查 Token 格式：Bearer <token>（中间有空格）

### 🔴 错误 4：500 Internal Server Error

**现象**：请求返回 500

**排查步骤**：
1. 查看后端服务日志（哪个服务报错）
2. 截图响应体中的 message 字段
3. 联系后端工程师处理

### 🔴 错误 5：网关 502/503

**现象**：请求返回 502 Bad Gateway 或 503 Service Unavailable

**排查步骤**：
1. 在 Nacos 控制台确认目标服务已注册
2. 重启对应服务
3. 检查服务健康状态：

`ash
curl http://localhost:10003/actuator/health  # del-user
curl http://localhost:10001/actuator/health  # del-order
curl http://localhost:10000/actuator/health  # del-product
`

---

## 📋 第五步：验证报告模板

完成验证后，请填写以下报告：

`
=== 前端 DevTools 联调验证报告 ===

【环境】
- 前端 dev server: http://localhost:5173 ✅/❌
- 网关: http://localhost:10010 ✅/❌
- Nacos: 已登录配置中心 ✅/❌

【批次 1 验证：C端核心流程】

| # | 接口 | URL | Status | 业务码 | 备注 |
|---|------|-----|--------|--------|------|
| 1 | 登录 | /api/v1/auth/login | 200/xxx | 0/xxx | |
| 2 | 发送验证码 | /api/v1/auth/send-code | 200/xxx | 0/xxx | |
| 3 | 注册 | /api/v1/auth/register | 200/xxx | 0/xxx | |
| 4 | 商品列表 | /api/v1/products | 200/xxx | 0/xxx | |
| 5 | 商品详情 | /api/v1/products/{id} | 200/xxx | 0/xxx | |
| 6 | 分类 | /api/v1/categories | 200/xxx | 0/xxx | |
| 7 | 购物车列表 | /api/v1/cart | 200/xxx | 0/xxx | |
| 8 | 加购 | /api/v1/cart/items | 200/xxx | 0/xxx | |
| 9 | 地址列表 | /api/v1/user/addresses | 200/xxx | 0/xxx | |
| 10 | 创建订单 | /api/v1/user/orders | 200/xxx | 0/xxx | ⭐ |
| 11 | 订单列表 | /api/v1/user/orders | 200/xxx | 0/xxx | ⭐ |
| 12 | 订单详情 | /api/v1/user/orders/{id} | 200/xxx | 0/xxx | ⭐ |
| 13 | 取消订单 | /api/v1/user/orders/{id}/cancel | 200/xxx | 0/xxx | ⭐ |
| 14 | 创建支付 | /api/v1/payments | 200/xxx | 0/xxx | |
| 15 | 点赞 | /api/v1/orders/{id}/items/{productId}/vote | 200/xxx | 0/xxx | ⭐ |

【批次 2 验证：评价与消息】

| 16 | 评价开关 | /api/v1/reviews/switch | 200/xxx | 0/xxx | ⭐ |
| 17 | 消息列表 | /api/v1/messages | 200/xxx | 0/xxx | |
| 18 | 标记已读 | /api/v1/messages/{id}/read | 200/xxx | 0/xxx | |

【发现问题】

| # | 问题 | 截图/请求URL | 责任方 |
|---|------|--------------|--------|
| 1 | | | 前端/后端 |
| 2 | | | |

【结论】

- [ ] 全部通过，可进入下一阶段
- [ ] 部分通过，待修复 X 个问题
- [ ] 不通过，需要重新联调

【截图】

（粘贴关键请求的截图）

=== 报告结束 ===
`

---

## 📋 第六步：问题反馈

### 反馈渠道

将验证报告发送至联调工作群，并 @ 相关责任人：

| 问题类型 | 责任人 |
|----------|--------|
| 前端代码问题（请求路径、参数） | 前端工程师 |
| 网关路由问题 | 后端工程师（路由） |
| 后端 Controller 问题 | 后端工程师（业务） |
| 数据库/Redis/MQ 问题 | 后端工程师（基础设施） |

### 反馈要求

1. **每个问题附上**：
   - 完整 Request URL
   - HTTP 状态码
   - 响应 Body（截图）
   - Network 面板截图
   - 复现步骤

2. **优先级**：
   - 🔴 P0：阻塞下单、支付等核心流程
   - 🟡 P1：影响辅助功能
   - 🟢 P2：体验优化

---

## 🔴 铁律

> **任何接口出现 404/500/超时，必须立即反馈，不得默认通过。**
>
> **理论可行≠实际可用，必须用真实请求验证。**

---

**完成验证后请提交报告，进入下一阶段。**

*指导文档由 Codex 验收评审生成*
