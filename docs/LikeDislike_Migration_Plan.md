# 商品点赞/踩功能 - 微服务迁移开发方案

---

## 项目当前状态

| 服务 | 端口 | 状态 |
|------|------|------|
| del-gateway | 10008 | 正常 |
| del-product | 10000 | 正常 |
| del-order | 10001 | 正常 |
| del-user | 10003 | 正常 |
| del-stats | 10009 | 正常 |

---

## 开发目标

将单体项目的商品点赞/踩功能迁移到 del-product 微服务，实现：

1. 用户点赞/踩：已完成订单的用户可对商品点赞或踩
2. 取消操作：用户可取消已点赞/踩
3. 计数缓存：L1 Caffeine + L2 Redis + L3 MySQL 三级缓存
4. 异步处理：MQ 解耦主流程
5. 管理开关：管理员可开启/关闭点赞/踩功能

---

## 数据库设计

### t_review 表

CREATE TABLE IF NOT EXISTS t_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  type TINYINT NOT NULL COMMENT '1=点赞 2=踩',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  INDEX idx_review_user (user_id),
  INDEX idx_review_product (product_id),
  UNIQUE KEY uk_order_product (order_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

---

## 新增文件清单

| 文件路径 | 说明 |
|---------|------|
| entity/Review.java | 评价实体类 |
| enums/ReviewType.java | 评价类型枚举 |
| mapper/ReviewMapper.java | MyBatis Plus Mapper |
| dto/VoteReq.java | 点赞请求DTO |
| dto/VoteResultVO.java | 点赞结果VO |
| dto/ReviewCountVO.java | 评价计数VO |
| service/ReviewService.java | 评价服务接口 |
| service/impl/ReviewServiceImpl.java | 评价服务实现 |
| service/ReviewCountCacheService.java | 缓存服务接口 |
| service/impl/ReviewCountCacheServiceImpl.java | 缓存服务实现 |
| controller/ProductController.java | 商品用户端API |
| controller/admin/AdminProductController.java | 商品管理端API |
| config/CacheConfig.java | Caffeine缓存配置 |
| config/RabbitMQConfig.java | RabbitMQ配置 |
| mq/VoteEvent.java | 点赞事件消息 |
| mq/VoteProducer.java | 点赞事件生产者 |
| mq/VoteConsumer.java | 点赞事件消费者 |

---

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/products/{productId}/vote | 点赞/踩/取消 |
| GET | /api/v1/products/{productId}/vote | 获取点赞信息 |
| GET | /api/v1/admin/products/vote/switch | 获取开关状态 |
| PUT | /api/v1/admin/products/vote/praise | 开启/关闭点赞 |
| PUT | /api/v1/admin/products/vote/bad | 开启/关闭踩 |

---

## 执行清单

1. 执行建表SQL
2. 添加Caffeine依赖到pom.xml
3. 创建枚举类 ReviewType
4. 创建实体类 Review
5. 创建Mapper ReviewMapper
6. 创建DTO
7. 创建配置类 CacheConfig, RabbitMQConfig
8. 创建MQ组件 VoteEvent, VoteProducer, VoteConsumer
9. 创建Service ReviewService, ReviewCountCacheService
10. 创建Controller
11. 编译验证
12. 启动测试

---

## 注意事项

1. Redis Key前缀: review:praise:open, review:bad:open
2. MQ队列: vote.exchange -> vote.queue
3. 缓存: L1 Caffeine(5min) -> L2 Redis(30min) -> L3 MySQL