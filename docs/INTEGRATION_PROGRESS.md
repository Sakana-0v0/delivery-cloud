# 📋 微服务联调进度报告

> 更新时间：2026-09-02

---

## ✅ C端联调 - 全部完成

### 批次1：核心业务流程

| 模块 | 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|------|
| **认证** | 登录 | POST | /api/v1/auth/login | ✅ 完成 |
| **认证** | 注册 | POST | /api/v1/auth/register | ✅ 完成 |
| **认证** | 发送验证码 | POST | /api/v1/auth/send-code | ✅ 完成 |
| **认证** | 刷新Token | POST | /api/v1/auth/refresh | ✅ 完成 |
| **商品** | 商品列表 | GET | /api/v1/products | ✅ 完成 |
| **商品** | 商品详情 | GET | /api/v1/products/{id} | ✅ 完成 |
| **商品** | 热门商品 | GET | /api/v1/products/hot | ✅ 完成 |
| **分类** | 分类列表 | GET | /api/v1/categories | ✅ 完成 |
| **购物车** | 获取购物车 | GET | /api/v1/cart | ✅ 完成 |
| **购物车** | 添加商品 | POST | /api/v1/cart/items | ✅ 完成 |
| **购物车** | 更新数量 | PUT | /api/v1/cart/items/{productId} | ✅ 完成 |
| **购物车** | 删除商品 | DELETE | /api/v1/cart/items/{productId} | ✅ 完成 |
| **购物车** | 清空购物车 | DELETE | /api/v1/cart | ✅ 完成 |
| **订单** | 创建订单 | POST | /api/v1/user/orders | ✅ 完成 |
| **订单** | 订单列表 | GET | /api/v1/user/orders | ✅ 完成 |
| **订单** | 订单详情 | GET | /api/v1/user/orders/{id} | ✅ 完成 |
| **订单** | 取消订单 | POST | /api/v1/user/orders/{id}/cancel | ✅ 完成 |
| **订单** | 确认收货 | POST | /api/v1/user/orders/{id}/confirm | ✅ 完成 |

### 批次2：配套功能

| 模块 | 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|------|
| **地址** | 地址列表 | GET | /api/v1/user/addresses | ✅ 完成 |
| **地址** | 地址详情 | GET | /api/v1/user/addresses/{id} | ✅ 完成 |
| **地址** | 新增地址 | POST | /api/v1/user/addresses | ✅ 完成 |
| **地址** | 更新地址 | PUT | /api/v1/user/addresses/{id} | ✅ 完成 |
| **地址** | 删除地址 | DELETE | /api/v1/user/addresses/{id} | ✅ 完成 |
| **地址** | 设置默认 | PUT | /api/v1/user/addresses/{id}/default | ✅ 完成 |
| **支付** | 创建支付 | POST | /api/v1/payments | ✅ 完成 |
| **支付** | 支付状态 | GET | /api/v1/payments/{orderNo} | ✅ 完成 |
| **评价** | 评价开关 | GET | /api/v1/reviews/switch | ✅ 完成 |
| **评价** | 点赞/点踩 | POST | /api/v1/orders/{orderId}/items/{productId}/vote | ✅ 完成 |

---

## 🟢 管理端联调 - 待开始

### 管理员接口清单

| 模块 | 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|------|
| **认证** | 管理员登录 | POST | /api/v1/admin/auth/login | 🔄 待测试 |
| **订单** | 订单分页 | GET | /api/v1/admin/orders | 🔄 待测试 |
| **订单** | 订单详情 | GET | /api/v1/admin/orders/{id} | 🔄 待测试 |
| **订单** | 修改状态 | PUT | /api/v1/admin/orders/{id}/status | 🔄 待测试 |
| **商品** | 商品管理 | GET/PUT | /api/v1/admin/products | 🔄 待测试 |
| **评价** | 👍开关 | PUT | /api/v1/admin/reviews/praise | 🔄 待测试 |
| **评价** | 👎开关 | PUT | /api/v1/admin/reviews/bad | 🔄 待测试 |
| **统计** | Dashboard | GET | /api/v1/admin/stats/overview | 🔄 待测试 |

---

## 📊 总体进度

```json
{
  "C端联调": { "total": 27, "completed": 27, "progress": "100%" },
  "管理端联调": { "total": 8, "completed": 0, "progress": "0%" }
}
```

---

## ⚠️ 已知问题

| 问题 | 模块 | 状态 | 备注 |
|------|------|------|------|
| OrderCreateReq @NotBlank 冲突 | 订单 | ✅ 已修复 | addressId 二选一时校验冲突 |
| 内部服务Token | 支付 | ✅ 已修复 | InternalServiceFeignInterceptor |
| 评价功能开关 | 评价 | ⚠️ 需管理员开启 | 当前 praiseOpen=false, badOpen=false |

---

## 🔜 下一步

1. 管理端联调测试
2. 前端 Admin 页面开发
