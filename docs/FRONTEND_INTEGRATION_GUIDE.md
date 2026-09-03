TEST_MARKER# 微服务项目配置清单 - 前端工程师参考

**版本**：v1.0  
**更新日期**：2026-09-02  
**项目**：delivery-cloud 外卖微服务平台

---

## ⚠️ 重要提示

前端工程师在对接后端微服务时，请**务必以本文档为准**。旧代码中的端口和路径可能已过时。

---

## 一、基础设施配置

### 1.1 基础地址

| 服务 | 地址 |
|------|------|
| **网关（前端对接入口）** | http://localhost:10010 |
| Nacos 配置中心 | http://127.0.0.1:8848 |
| MySQL | 127.0.0.1:3306 |
| Redis | 127.0.0.1:6379 |
| RabbitMQ | 127.0.0.1:5672 / 15672 |
| MinIO | http://127.0.0.1:9000 |

### 1.2 Nacos 配置

| 项目 | 值 |
|------|---|
| 地址 | 127.0.0.1:8848 |
| Namespace | sakana |
| Group | DEFAULT_GROUP |

---

## 二、服务端口清单（后端内部服务）

| 服务 | 端口 | 数据库 | 说明 |
|------|------|--------|------|
| **del-gateway** | **10010** | - | ⚠️ **前端对接入口** |
| del-user | 10003 | del_user_db | 用户服务 |
| del-product | 10000 | del_product_db | 商品服务 |
| del-order | 10001 | del_order_db | 订单服务 |
| del-admin | 10002 | del_admin_db | 管理后台 |
| del-payment | 10004 | del_payment_db | 支付服务 |
| del-message | 10005 | del_message_db | 消息服务 |
| del-stats | 10009 | del_stats_db | 统计服务 |
| del-file | 10006 | del_file_db | 文件上传服务 |

---

## 三、网关路由配置（✅ 最新）

### 3.1 前端对接基础路径

`http://localhost:10010/api/v1/...`

### 3.2 路由映射表

| 网关路径 | 目标服务 | 后端实际路径 | 说明 |
|----------|----------|--------------|------|
| /api/v1/auth/** | del-user | /api/v1/auth/** | 登录/注册/登出 |
| /api/v1/user/** | del-user | /api/v1/user/** | 用户信息/地址 |
| /api/v1/addresses/** | del-user | /api/v1/addresses/** | 地址（直接透传） |
| /api/v1/products/** | del-product | /api/v1/products/** | 商品列表/详情 |
| /api/v1/categories/** | del-product | /api/v1/categories/** | 分类列表 |
| /api/v1/orders/*/items/*/vote | del-product | /api/v1/orders/*/items/*/vote | 商品点赞/踩 |
| /api/v1/orders/** | del-order | /api/v1/orders/** | 订单（用户端） |
| /api/v1/cart/** | del-order | /api/v1/cart/** | 购物车 |
| /api/v1/payments/** | del-payment | /api/v1/payments/** | 支付 |
| /api/v1/messages/** | del-message | /api/v1/messages/** | 消息 |
| /api/v1/files/** | del-file | /api/v1/files/** | 文件上传 |
| /api/v1/stats/** | del-stats | /api/v1/stats/** | 统计 |
| /api/v1/admin/** | del-admin | /api/v1/admin/** | 管理后台 |

---

## 四、API 接口路径（前端直接调用）

### 4.1 C端用户接口

#### 认证服务 /api/v1/auth/ → del-user

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 发送验证码 | POST | /api/v1/auth/send-code | ❌ | body: {email} |
| 用户注册 | POST | /api/v1/auth/register | ❌ | body: {username, password, email, nickname, code} |
| 用户登录 | POST | /api/v1/auth/login | ❌ | body: {username, password} |
| 刷新Token | POST | /api/v1/auth/refresh | ❌ | body: {refreshToken} |
| 登出 | POST | /api/v1/auth/logout | ✅ | - |

#### 收货地址 /api/v1/user/addresses/ → del-user

| 接口 | 方法 | 路径 | 认证 |
|------|------|------|------|
| 地址列表 | GET | /api/v1/user/addresses | ✅ |
| 地址详情 | GET | /api/v1/user/addresses/{id} | ✅ |
| 新增地址 | POST | /api/v1/user/addresses | ✅ |
| 修改地址 | PUT | /api/v1/user/addresses/{id} | ✅ |
| 删除地址 | DELETE | /api/v1/user/addresses/{id} | ✅ |
| 设为默认 | PUT | /api/v1/user/addresses/{id}/default | ✅ |

#### 商品服务 /api/v1/products/ → del-product

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 商品列表 | GET | /api/v1/products | ❌ | ?categoryId=&keyword=&page=&size= |
| 商品详情 | GET | /api/v1/products/{id} | ❌ | - |
| 热门商品 | GET | /api/v1/products/hot | ❌ | ?limit= |

#### 分类服务 /api/v1/categories/ → del-product

| 接口 | 方法 | 路径 | 认证 |
|------|------|------|------|------|
| 分类列表 | GET | /api/v1/categories | ❌ |

#### 购物车 /api/v1/cart/ → del-order

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 查看购物车 | GET | /api/v1/cart | ✅ | - |
| 添加商品 | POST | /api/v1/cart/items | ✅ | body: {productId, quantity} |
| 修改数量 | PUT | /api/v1/cart/items/{productId} | ✅ | body: {quantity} |
| 删除商品 | DELETE | /api/v1/cart/items/{productId} | ✅ | - |
| 清空购物车 | DELETE | /api/v1/cart | ✅ | - |

#### 订单服务 /api/v1/orders/ → del-order

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 创建订单 | POST | /api/v1/user/orders | ✅ | body: {addressId, paymentMethod, items: [{productId, quantity}]} |
| 订单列表 | GET | /api/v1/user/orders | ✅ | ?status=&page=&size= |
| 订单详情 | GET | /api/v1/user/orders/{id} | ✅ | - |
| 取消订单 | PUT | /api/v1/user/orders/{id}/cancel | ✅ | - |
| 确认收货 | PUT | /api/v1/user/orders/{id}/confirm | ✅ | - |

#### 评价服务 /api/v1/reviews/ → del-product

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 评价开关 | GET | /api/v1/reviews/switch | ❌ | 返回 {praiseOpen, badOpen} |
| 点赞/踩 | POST | /api/v1/orders/{orderId}/items/{productId}/vote | ✅ | body: {type: 1good/2bad} |

#### 支付服务 /api/v1/payments/ → del-payment

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 创建支付 | POST | /api/v1/payments/create | ✅ | body: {orderNo, payMethod} |
| 支付状态 | GET | /api/v1/payments/{orderNo} | ✅ | - |

### 4.2 管理后台接口（需管理员 Token）

#### 管理员认证 /api/v1/admin/auth/ → del-admin

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 管理员登录 | POST | /api/v1/admin/auth/login | ❌ | body: {username, password} |

#### 管理员订单 /api/v1/admin/orders/ → del-order

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 订单列表 | GET | /api/v1/admin/orders | ✅ | ?status=&page=&size= |
| 订单详情 | GET | /api/v1/admin/orders/{id} | ✅ | - |
| 修改状态 | PUT | /api/v1/admin/orders/{id}/status | ✅ | body: {status} |

#### 管理员商品 /api/v1/admin/products/ → del-product

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 商品列表 | GET | /api/v1/admin/products | ✅ | ?categoryId=&keyword=&page=&size= |
| 商品详情 | GET | /api/v1/admin/products/{id} | ✅ | - |
| 新增商品 | POST | /api/v1/admin/products | ✅ | body: {name, price, categoryId, cover, description} |
| 修改商品 | PUT | /api/v1/admin/products/{id} | ✅ | body: {name, price, categoryId, cover, description} |
| 上架/下架 | PUT | /api/v1/admin/products/{id}/status | ✅ | body: {status: 0/1} |

#### 管理员分类 /api/v1/admin/categories/ → del-product

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 分类列表 | GET | /api/v1/admin/categories | ✅ | - |
| 新增分类 | POST | /api/v1/admin/categories | ✅ | body: {name, icon, sort} |
| 修改分类 | PUT | /api/v1/admin/categories/{id} | ✅ | body: {name, icon, sort} |
| 删除分类 | DELETE | /api/v1/admin/categories/{id} | ✅ | - |

#### 管理员用户 /api/v1/admin/users/ → del-user（Feign）

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 用户列表 | GET | /api/v1/admin/users | ✅ | ?keyword=&status=&page=&size= |
| 用户详情 | GET | /api/v1/admin/users/{id} | ✅ | - |
| 修改状态 | PUT | /api/v1/admin/users/{id}/status | ✅ | body: {status: 0/1} |

#### 管理员收货地址 /api/v1/admin/addresses/ → del-user（Feign）

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 地址列表 | GET | /api/v1/admin/addresses | ✅ | ?userId=&keyword=&page=&size= |
| 删除地址 | DELETE | /api/v1/admin/addresses/{id} | ✅ | - |

#### 管理员统计 /api/v1/admin/stats/ → del-stats

| 接口 | 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|------|
| 运营概览 | GET | /api/v1/admin/stats/overview | ✅ | - |
| 销售统计 | GET | /api/v1/admin/stats/sales | ✅ | ?startDate=&endDate= |
| 热销商品 | GET | /api/v1/admin/stats/hot-products | ✅ | ?limit= |

---

## 五、创建订单 API 变更说明

### 5.1 新版订单创建（2026-09-02 修复）

**请求路径**：`POST /api/v1/user/orders`

**请求头**：`Authorization: Bearer <token>`

**请求体**：

```json
{
  "addressId": 123,
  "paymentMethod": 1,
  "items": [
    { "productId": 1, "quantity": 2 }
  ],
  "remark": "少放辣"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| addressId | Long | ✅ | 收货地址 ID |
| paymentMethod | Integer | ✅ | 1=微信支付, 2=支付宝 |
| items | Array | ✅ | 商品列表 |
| items[].productId | Long | ✅ | 商品 ID |
| items[].quantity | Integer | ✅ | 数量 |
| remark | String | ❌ | 备注 |

**说明**：
- 不再需要传详细的收货人信息（receiverName/Phone/Address）
- 商品快照由后端自动通过 Feign 调用 del-product 服务补全
- 雪花算法生成的 orderId 会暴露给前端

---

## 六、测试账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 普通用户 | testuser | Test@123 | C端测试账号 |
| 管理员 | superadmin | 123456 | B端管理员账号 |

---

**文档版本**: v1.10  
**生成日期**: 2026-09-01  
**生成依据**: delivery-cloud 微服务项目实际配置

---

## 七、联调进度跟踪

> 更新日期：2026-09-02

### 7.1 模块联调状态

| 模块 | 接口 | 后端状态 | 前端状态 | 说明 |
|------|------|---------|---------|------|
| 认证 | /api/v1/auth/** | ✅ 正常 | ✅ 正常 | JWT 验签/刷新/登出 |
| 商品浏览 | /api/v1/products | ✅ 正常 | ✅ 正常 | 图片已从 MinIO 加载 |
| 分类 | /api/v1/categories | ✅ 正常 | ✅ 正常 | - |
| 收货地址 | /api/v1/user/addresses | ✅ 正常 | ✅ 正常 | - |
| 购物车 | /api/v1/cart/** | ✅ 正常 | ✅ 正常 | - |
| 下单 | /api/v1/user/orders | ✅ 正常 | ✅ 正常 | Feign内部调用已修复 |
| 支付 | /api/v1/payments/** | ✅ 正常 | 🔄 待验证 | 需真实支付宝测试 |
| 评价 | /api/v1/reviews/** | ✅ 正常 | 🔄 待联调 | 点赞/踩功能 |
| 文件上传 | /api/v1/files/** | ✅ 正常 | 🔄 待扩展 | 管理员商品图片上传 |
| **管理员统计** | /api/v1/admin/stats/** | ✅ 正常 | ✅ 已完成 | - |
| **管理员订单** | /api/v1/admin/orders/** | ✅ 正常 | ✅ 已完成 | - |
| **管理员商品** | /api/v1/admin/products/** | ✅ 正常 | ✅ 已完成 | - |
| **管理员分类** | /api/v1/admin/categories/** | ✅ 正常 | ✅ 已完成 | - |
| **管理员用户** | /api/v1/admin/users/** | ✅ 正常 | ✅ 已完成 | Feign @SpringQueryMap 已修复 |
| **邮件服务** | /api/v1/messages/** | ✅ 正常 | ✅ 已完成 | Redis Lettuce RESP2 已修复 |

### 7.2 已解决问题记录

| 日期 | 问题 | 解决方案 | 状态 |
|------|------|---------|------|
| 2026-09-01 | CORS 双值 , * | 业务服务移除 Security CORS 配置 | ✅ |
| 2026-09-01 | 网关订单路由 /api/v1/user/orders 冲突 | 移至 del-order，移除 del-user 的 /user/** | ✅ |
| 2026-09-01 | 评价路由缺失 | /api/v1/reviews/** 添加至 del-product | ✅ |
| 2026-09-01 | 注册接口参数不完整 | 前端新增 nickname + code 参数 | ✅ |
| 2026-09-01 | 前端网关端口错误 | .env 改为 VITE_API_BASE_URL=http://localhost:10010/api/v1 | ✅ |
| 2026-09-01 | del-order RabbitMQConfig Bean 冲突 | 重命名为 OrderRabbitMQConfig + @Qualifier | ✅ |
| 2026-09-01 | del-order 打包异常 | spring-boot-maven-plugin 添加 executions 配置 | ✅ |
| 2026-09-01 | del-message RabbitListener 队列名空 | @RabbitListener queues 从空字符串改为配置 | ✅ |
| 2026-09-01 | del-message MessageConverter 白名单 | 添加 java.util.*, java.lang.*, java.time.* | ✅ |
| 2026-09-01 | del-message 邮件 HTML 格式化错误 | Text Block 中双重引号改为单引号 | ✅ |
| 2026-09-02 | del-file Spring Security 拦截 | FileApplication 排除 SecurityAutoConfiguration | ✅ |
| 2026-09-02 | 菜品图片缺失（首次上传） | 18张图片上传至 MinIO，数据库 cover 已更新 | ✅ |
| 2026-09-03 | 商品列表图片全部加载失败（C1） | 占位URL+private bucket+空bucket问题，18张真实图片重新上传MinIO并设为public | ✅ |
| 2026-09-02 | 管理员前端页面缺失 | 6个管理页面 + 5个API模块全部交付 | ✅ |
| 2026-09-02 | 邮件真实发送 | SMTP 真实投递验证通过 | ✅ |
| 2026-09-02 | 订单创建 Feign 内部调用失败 | 修复 del-user /internal/** 鉴权 + OrderCreateReq 验证冲突 | ✅ |
| 2026-09-02 | del-admin Feign GET POJO 参数传递失败 | UserFeignClient 添加 @SpringQueryMap 注解 | ✅ |
| 2026-09-02 | del-admin InternalServiceFeignInterceptor 未生效 | AdminApplication 添加 @Import(InternalServiceFeignInterceptor.class) | ✅ |
| 2026-09-02 | del-message Redis Lettuce NOAUTH 失败 | del-common 添加 RedisConfig 强制 RESP2 协议 | ✅ |
| 2026-09-02 | del-admin CORS 双值问题 | AdminSecurityConfig 移除 corsConfigurationSource 配置 | ✅ |
| 2026-09-02 |
| 2026-09-02 | Dashboard Vite 编译错误（trendMax栈溢出 + 模板:class损坏 + %解析错误） | P0 | 前端修复：reduce替代spread、修复:class绑定、对象式:style、@error箭头函数、类型断言 | ✅ | stats.ts返回类型修正+DashboardView提取.points+字段名对齐+order.ts PUT方法修正 | 修复完成 | 管理后台切换页面卡死 | 6个页面添加 cancelled + onUnmounted，AdminLayout 移除过渡动画 | ✅ |

#
## 8. P0 紧急修复记录（2026-09-02）

| 日期 | 问题 | 根因 | 解决方案 | 状态 |
|------|------|------|---------|------|
| 2026-09-02 | Dashboard sales.value.map 崩溃 | 后端返回 {granularity, points} 对象，前端类型声明为 SalesPoint[]，axios解包后.map()报错 | stats.ts新增SalesTrendResp接口，getSalesTrend()返回Promise<SalesTrendResp>；DashboardView取值改为sl.value?.points or[]；模板item.date改为item.dateKey | 修复完成 |
| 2026-09-02 | Dashboard统计卡片字段名不匹配 | 后端返回todayOrderCount/todaySalesAmount/totalUserCount/todayNewUserCount，前端OverviewStats接口字段不一致 | stats.ts的OverviewStats接口字段全部对齐；DashboardView默认值和模板引用同步修正 | 修复完成 |
| 2026-09-02 | 取消/确认收货HTTP方法错误 | 后端是PUT /user/orders/{id}/cancel|confirm，前端order.ts用了post | cancelOrder()和confirmOrder()改为request.put() | 修复完成 |

## 7.3 当前阻塞问题

#### 🟡 待验证项

| 项目 | 说明 | 备注 |
|------|------|------|
| 支付回调 | 需完成下单联调后验证 | 需真实支付宝沙箱测试 |
| 评价功能 | /api/v1/reviews/** | 待前后端联调验证 |
| 订单列表/取消/确认收货 | /api/v1/user/orders | 待前端联调测试 |

#### ✅ 已完成项

| 项目 | 完成日期 |
|------|---------|
| 管理员前端页面 | 2026-09-02 |
| 邮件真实发送 | 2026-09-02 |
| 订单创建功能 | 2026-09-02 |
| 收货地址功能 | 2026-09-02 |
| 购物车功能 | 2026-09-02 |
| 管理员用户管理（Feign） | 2026-09-02 |
| del-message Redis 连接 | 2026-09-02 |
| 管理端 CORS 双值修复 | 2026-09-02 |

### 7.4 菜品图片映射（已完成）

| ID | 旧文件名 | 菜品名 | MinIO URL 状态 |
|----|---------|--------|---------------|
| 1 | 500008.jpg | 酸辣凉拌土豆丝 | ✅ 已上传 |
| 2 | 500022.jpg | 葱花米饭 | ✅ 已上传 |
| 3 | 500023.jpg | 番茄鱼汤 | ✅ 已上传 |
| 4 | 500024.jpg | 红油拌面 | ✅ 已上传 |
| 5 | 500025.jpg | 番茄菠菜鸡蛋汤 | ✅ 已上传 |
| 6 | 500026.jpg | 砂锅老鸭汤 | ✅ 已上传 |
| 7 | 500033.jpg | 酸豆角拌肉丝 | ✅ 已上传 |
| 8 | 500034.jpg | 清炖排骨汤 | ✅ 已上传 |
| 9 | 500035.jpg | 番茄炒蛋 | ✅ 已上传 |
| 10 | 500036.jpg | 蒜蓉肉片蒸茄子 | ✅ 已上传 |
| 11 | 500038.jpg | 蒜蓉清炒青菜 | ✅ 已上传 |
| 12 | 500041.jpg | 酸豆角炒肉丝 | ✅ 已上传 |
| 13 | 500042.jpg | 排骨粉丝清汤 | ✅ 已上传 |
| 14 | 500043.jpg | 西红柿炒鸡蛋 | ✅ 已上传 |
| 15 | 500044.jpg | 肉末蒸茄子 | ✅ 已上传 |
| 16 | 500045.jpg | 老北京炸酱面 | ✅ 已上传 |
| 17 | 500046.jpg | 清炒绿叶时蔬 | ✅ 已上传 |
| 18 | 500047.jpg | 皮蛋瘦肉粥 | ✅ 已上传 |

---

**文档版本**: v1.10  
**更新日期**: 2026-09-02  
**更新内容**: P0修复（Dashboard崩溃修复 + 订单PUT方法修正）





