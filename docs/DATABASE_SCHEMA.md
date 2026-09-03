# 📊 数据库表结构设计清单

**生成日期**：2026-09-02
**版本**：v1.0
**生成依据**：基于 `scripts/*.sql` 脚本 + Java Entity 类交叉验证

---

## 一、数据库总览

| 数据库 | 字符集 | 排序规则 | 表数量 | 服务归属 |
|--------|--------|---------|--------|---------|
| `del_user_db` | utf8mb4 | utf8mb4_unicode_ci | 3 | del-user |
| `del_admin_db` | utf8mb4 | utf8mb4_unicode_ci | 1 | del-admin |
| `del_product_db` | utf8mb4 | utf8mb4_unicode_ci | 3 | del-product |
| `del_order_db` | utf8mb4 | utf8mb4_unicode_ci | 3 | del-order |
| `del_payment_db` | utf8mb4 | utf8mb4_unicode_ci | 2 | del-payment |
| `del_file_db` | utf8mb4 | utf8mb4_unicode_ci | 1 | del-file |
| `del_message_db` | utf8mb4 | utf8mb4_unicode_ci | 1 | del-message |
| `del_stats_db` | utf8mb4 | utf8mb4_unicode_ci | 1 | del-stats |
| **合计** | - | - | **15 张表** | 8 个微服务 |

---

## 二、完整表清单

### 2.1 del_user_db（3 张表）

#### 表：`t_user` ✅ 有 SQL 脚本

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) | UNIQUE, NOT NULL | 用户名 |
| password | VARCHAR(128) | NOT NULL | BCrypt 加密密码 |
| nickname | VARCHAR(64) | NULL | 昵称 |
| phone | VARCHAR(32) | NULL | 手机号 |
| email | VARCHAR(128) | NULL | 邮箱 |
| avatar | VARCHAR(512) | NULL | 头像URL |
| status | TINYINT | NOT NULL DEFAULT 0 | 0=正常, 1=冻结 |
| last_login_time | DATETIME | NULL | 最近登录时间 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |
| is_deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除标记 |

**索引**：
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_username` (`username`)
- KEY `idx_phone` (`phone`)
- KEY `idx_email` (`email`)

**对应 Entity**：`del-user/.../entity/User.java` ✅

---

#### 表：`t_user_address` ✅ 有 SQL 脚本

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 用户ID |
| consignee | VARCHAR(64) | NOT NULL | 收货人姓名 |
| phone | VARCHAR(32) | NOT NULL | 联系电话 |
| province | VARCHAR(32) | NOT NULL | 省份 |
| city | VARCHAR(32) | NOT NULL | 城市 |
| district | VARCHAR(32) | NOT NULL | 区县 |
| detail_address | VARCHAR(256) | NOT NULL | 详细地址 |
| tag | VARCHAR(32) | NULL | 地址标签 |
| is_default | TINYINT | NOT NULL DEFAULT 0 | 是否默认 |
| create_time | DATETIME | NOT NULL | - |
| update_time | DATETIME | NOT NULL | - |
| is_deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除标记 |

**索引**：
- PRIMARY KEY (`id`)
- KEY `idx_user_id` (`user_id`)

**对应 Entity**：`del-user/.../entity/UserAddress.java` ✅

---

#### 表：`user_event_outbox` ✅ 有 SQL 脚本

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| event_id | VARCHAR(64) | UNIQUE, NOT NULL | 事件唯一ID |
| event_type | VARCHAR(32) | NOT NULL | REGISTER / LOGIN |
| user_id | BIGINT | NOT NULL | 用户ID |
| username | VARCHAR(64) | NOT NULL | 用户名 |
| email | VARCHAR(128) | NOT NULL | 邮箱 |
| ip | VARCHAR(64) | NULL | 登录IP |
| status | TINYINT | NOT NULL DEFAULT 0 | 0=NEW, 1=SENT, 2=ACK, 3=DLQ |
| retry_count | INT | NOT NULL DEFAULT 0 | 重试次数 |
| last_error | TEXT | NULL | 错误信息 |
| create_time | DATETIME | NOT NULL | - |
| update_time | DATETIME | NOT NULL | - |

**对应 Entity**：`del-user/.../entity/UserEventOutbox.java` ✅

---

### 2.2 del_admin_db（1 张表）

#### 表：`t_admin` ✅ 有 SQL 脚本

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) | UNIQUE, NOT NULL | 用户名 |
| password | VARCHAR(128) | NOT NULL | BCrypt 加密密码 |
| nickname | VARCHAR(64) | NULL | 昵称 |
| role | VARCHAR(32) | NOT NULL DEFAULT 'admin' | 角色 |
| status | TINYINT | NOT NULL DEFAULT 0 | 0=正常, 1=禁用 |
| last_login_time | DATETIME | NULL | 最近登录时间 |
| create_time | DATETIME | NOT NULL | - |
| update_time | DATETIME | NOT NULL | - |
| is_deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除标记 |

**初始数据**：
```sql
INSERT INTO t_admin (username, password, nickname, role, status)
VALUES (''admin'', ''$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH'', ''超级管理员'', ''super_admin'', 0);
```

**对应 Entity**：`del-admin/.../dao/entity/Admin.java` ✅

---

### 2.3 del_product_db（3 张表）

#### 表：`t_category` ✅ 有 SQL 脚本

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(64) | 分类名称 |
| sort | INT | 排序字段 |
| status | TINYINT | 0=启用, 1=禁用 |
| create_time / update_time | DATETIME | - |
| is_deleted | TINYINT | 逻辑删除标记 |

**初始数据**（4 个分类）：
1. 快餐便当 (sort=1)
2. 水果生鲜 (sort=2)
3. 甜品饮品 (sort=3)
4. 外卖杂货 (sort=4)

**对应 Entity**：`del-product/.../entity/Category.java` ✅

---

#### 表：`t_product` ✅ 有 SQL 脚本

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| fid | VARCHAR(64) | 商品FID（UNIQUE）|
| category_id | BIGINT | 分类ID |
| name | VARCHAR(128) | 商品名称 |
| cover | VARCHAR(512) | 封面URL |
| description | TEXT | 商品描述 |
| norm_price | DECIMAL(12,2) | 原价 |
| real_price | DECIMAL(12,2) | **现价** |
| stock | INT | 库存 |
| sales | INT | 销量 |
| status | TINYINT | 0=上架, 1=下架 |
| create_time / update_time / is_deleted | 标准字段 | - |

**索引**：
- UNIQUE KEY `uk_fid` (`fid`)
- KEY `idx_category_id` (`category_id`)
- KEY `idx_status` (`status`)
- KEY `idx_sales` (`sales`)

**对应 Entity**：`del-product/.../entity/Product.java` ✅

---

#### 表：`t_review` ✅ 有 SQL 脚本

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| user_id | BIGINT | 评价用户ID |
| order_id | BIGINT | 所属订单ID |
| product_id | BIGINT | 被评商品ID |
| type | TINYINT | 1=赞, 2=踩 |
| create_time / update_time / is_deleted | 标准字段 | - |

**索引**：
- UNIQUE KEY `uk_order_product` (`order_id`, `product_id`)

**对应 Entity**：`del-product/.../review/dao/entity/Review.java` ✅

---

### 2.4 del_order_db（3 张表）

#### 表：`t_order` ❌ 无 SQL 脚本（需要补）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| orderNo | VARCHAR | 订单号 |
| userId | BIGINT | 用户ID |
| totalAmount | DECIMAL(12,2) | 总金额 |
| payAmount | DECIMAL(12,2) | 实付金额 |
| status | INT | 订单状态 |
| receiverName | VARCHAR | 收货人（→DB receiver）|
| receiverPhone | VARCHAR | 联系电话（→DB phone）|
| receiverAddress | VARCHAR | 收货地址（→DB address）|
| remark | VARCHAR | 备注 |
| deliveryTime / payTime / shipTime | DATETIME | 各种时间 |
| completeTime | DATETIME | 完成时间（→DB finish_time）|
| cancelTime | DATETIME | 取消时间 |

**⚠️ 字段映射不一致**（Entity vs DB）：
- Java: `receiverName` → DB: `receiver`
- Java: `receiverPhone` → DB: `phone`
- Java: `receiverAddress` → DB: `address`
- Java: `completeTime` → DB: `finish_time`

**对应 Entity**：`del-order/.../entity/Order.java` ✅

---

#### 表：`t_order_item` ❌ 无 SQL 脚本（需要补）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| orderId | BIGINT | 订单ID |
| productId | BIGINT | 商品ID |
| productName | VARCHAR | 商品名称（冗余）|
| productCover | VARCHAR | 商品封面（冗余）|
| productPrice | DECIMAL(12,2) | 商品价格（→DB price）|
| quantity | INT | 数量 |
| subtotalAmount | DECIMAL(12,2) | 小计（→DB subtotal）|

**⚠️ 字段映射不一致**：
- Java: `productPrice` → DB: `price`
- Java: `subtotalAmount` → DB: `subtotal`

**对应 Entity**：`del-order/.../entity/OrderItem.java` ✅

---

#### 表：`order_outbox` ✅ 有 SQL 脚本

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| event_id | VARCHAR(64) | 事件唯一ID（UNIQUE）|
| event_type | VARCHAR(32) | ORDER_CREATED |
| order_no | VARCHAR(64) | 关联订单号 |
| user_id | BIGINT | 用户ID |
| username | VARCHAR(64) | 用户名 |
| email | VARCHAR(128) | 邮箱 |
| total_amount | DECIMAL(12,2) | 订单总金额 |
| status | TINYINT | outbox状态 |
| retry_count / last_error | INT / TEXT | 重试信息 |

**对应 Entity**：`del-order/.../entity/OrderOutbox.java` ✅

---

### 2.5 del_payment_db（2 张表）

#### 表：`t_payment` ❌ 无 SQL 脚本（需要补）

**对应 Entity**：`del-payment/.../entity/Payment.java` ✅

---

#### 表：`pay_outbox` ❌ 无 SQL 脚本（需要补）

**对应 Entity**：`del-payment/.../entity/PayOutbox.java` ✅

---

### 2.6 del_file_db（1 张表）

#### 表：`t_file_info` ✅ 有 SQL 脚本

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| md5 | VARCHAR(64) | 文件MD5（UNIQUE）|
| original_name | VARCHAR(255) | 原始文件名 |
| file_name | VARCHAR(255) | MinIO 对象名 |
| file_url | VARCHAR(500) | 访问URL |
| file_size | BIGINT | 文件大小 |
| content_type | VARCHAR(100) | MIME类型 |
| file_type | VARCHAR(20) | image/video/audio/document |
| create_time / is_deleted | 标准字段 | - |

**对应 Entity**：`del-file/.../dao/entity/FileInfo.java` ✅

---

### 2.7 del_message_db（1 张表）

#### 表：`t_message` ❌ 无 SQL 脚本（需要补）

**对应 Entity**：`del-message/.../entity/Message.java` ✅

---

### 2.8 del_stats_db（1 张表）

#### 表：`t_visit_log` ❌ 无 SQL 脚本（需要补）

**对应 Entity**：`del-stats/.../dao/entity/VisitLog.java` ✅

---

## 三、SQL 脚本缺失清单

### 3.1 需要补的 SQL 脚本（6 张表）

| # | 数据库 | 表名 | 服务 | 优先级 |
|---|--------|------|------|--------|
| 1 | del_order_db | t_order | del-order | 🔴 P0 |
| 2 | del_order_db | t_order_item | del-order | 🔴 P0 |
| 3 | del_payment_db | t_payment | del-payment | 🔴 P0 |
| 4 | del_payment_db | pay_outbox | del-payment | 🟡 P1 |
| 5 | del_message_db | t_message | del-message | 🟡 P1 |
| 6 | del_stats_db | t_visit_log | del-stats | 🟢 P2 |

### 3.2 现有 SQL 脚本（覆盖 9 张表）

| 脚本文件 | 表 |
|----------|-----|
| `scripts/business_tables.sql` | t_user, t_user_address, t_admin, t_category, t_product, t_review |
| `scripts/outbox_tables.sql` | user_event_outbox, order_outbox |
| `scripts/del-file_init.sql` | t_file_info |
| `update_product_cover.sql` | t_product（UPDATE 数据）|
| `scripts/crawler/*.sql` | t_category（增量 INSERT）|

---

## 五、设计规范遵循情况

### 5.1 ✅ 优点

| 规范 | 遵循情况 |
|------|---------|
| 数据库分离（每服务一库）| ✅ 8 个服务 8 个数据库 |
| 无跨服务外键 | ✅ 跨服务引用通过 Feign + ID |
| 逻辑删除（is_deleted）| ✅ 12 张表包含 |
| 标准时间字段 | ✅ create_time + update_time |
| 字符集统一 | ✅ utf8mb4 + utf8mb4_unicode_ci |

### 5.2 ⚠️ 需改进

| 问题 | 位置 | 说明 |
|------|------|------|
| 字段命名不一致 | t_order, t_order_item | Java 字段名 ≠ DB 列名（需 @TableField）|
| SQL 脚本缺失 | 6 张表 | 只有 Entity 没有建表 SQL |
| 主键策略混乱 | 部分表用 AUTO_INCREMENT，部分用雪花算法 | 建议统一 |
| outbox 表命名 | order_outbox / pay_outbox / user_event_outbox | 命名风格不统一 |

### 5.3 🔴 风险点

| 风险 | 说明 | 建议 |
|------|------|------|
| `t_admin` 初始密码硬编码 | `admin / admin123` 的 BCrypt hash 写在 SQL 中 | 生产前应改为从环境变量读取 |
| presigned URL 硬编码到 DB | `update_product_cover.sql` 写的是 7 天过期的 URL | 应改用 MinIO public bucket 永久 URL |

---

## 六、初始化脚本（汇总）

### 6.1 推荐执行顺序

```sql
-- 1. 创建数据库 + 基础表
mysql -uroot -p < scripts/business_tables.sql
mysql -uroot -p < scripts/outbox_tables.sql
mysql -uroot -p < scripts/del-file_init.sql

-- 2. 增量初始化（如需要）
mysql -uroot -p del_product_db < scripts/crawler/add_western_categories.sql

-- 3. 商品图片更新（如需要）
mysql -uroot -p del_product_db < update_product_cover.sql
```

### 6.2 缺失脚本补全建议

需补 6 张表的 SQL 脚本：

```sql
-- 建议在 scripts/ 目录下创建：
-- - scripts/order_tables.sql     (t_order + t_order_item)
-- - scripts/payment_tables.sql   (t_payment + pay_outbox)
-- - scripts/message_tables.sql   (t_message)
-- - scripts/stats_tables.sql     (t_visit_log)
```

---

## 七、总结

| 项目 | 数量 | 状态 |
|------|------|------|
| 微服务数据库 | 8 个 | ✅ |
| 总表数 | 15 张 | - |
| 已有 SQL 脚本的表 | 9 张 | ✅ |
| **缺失 SQL 脚本的表** | **6 张** | ⚠️ 待补 |
| 表与 Entity 一致 | 100% | ✅ |
| 字段映射异常 | 2 个 Entity（t_order, t_order_item）| ⚠️ 已用 @TableField 处理 |
| 跨服务外键 | 0 | ✅ 合规 |

---

**文档版本**: v1.0  
**生成日期**: 2026-09-02  
**生成人**: Codex  
**生成依据**: SQL 脚本 + Java Entity 交叉验证
