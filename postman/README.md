# delivery-cloud Integration Tests

## 文件说明

| 文件 | 说明 |
|------|------|
| `delivery-cloud-tests.json` | Postman Collection (导入时选择此文件) |
| `delivery-cloud.postman_environment.json` | Postman Environment |

## 导入步骤

1. 打开 Postman
2. 点击 **Import** 按钮
3. 选择 `delivery-cloud-tests.json` 文件
4. 选择 `delivery-cloud.postman_environment.json` 文件导入环境变量
5. 在 Postman 右侧选择 **delivery-cloud Integration Environment** 环境

## 测试执行顺序

### Phase 1: 前置验证
- `00_Prerequisites` - 验证基础设施在线

### Phase 2: 核心流程
- `01_Auth_Flow` - 用户注册/登录
- `02_Product_Management` - 商品浏览/购物车
- `03_Order_Processing` - 订单创建/查询
- `04_Payment_Flow` - 支付流程

### Phase 3: 管理员操作
- `05_Admin_Operations` - 管理员商品上架

### Phase 4: 网关与消息
- `06_Gateway_Routing` - 网关路由验证
- `07_Message_Events` - MQ事件验证

### Phase 5: 内部API
- `08_Internal_Stats` - 内部统计API

### Phase 6: 安全测试
- `09_Security_Tests` - JWT鉴权/Token安全
- `10_Exception_Scenarios` - 异常场景

## 注意事项

1. 确保所有服务已启动并正常运行
2. 确保 Nacos, MySQL, RabbitMQ, Redis 已启动
3. 安全测试用例需要按顺序执行(先执行认证获取Token)
4. 部分测试用例之间有依赖关系，建议按文件夹顺序执行

## 测试数据

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 普通用户 | testuser | Test@123 |
| 管理员 | admin | Admin@123 |

| 服务 | Header | Value |
|------|--------|-------|
| Internal API | X-Internal-Service-Token | internal-service-secret-key-2024 |
