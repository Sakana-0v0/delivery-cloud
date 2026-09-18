# Performance Test Setup Guide

> 让 `docs/perf/jmx/*.jmx` 可一键复现的最小环境搭建指南。
> 完成本文档所有步骤后，可执行 `docs/perf/README.md` 中的 4 个压测命令。

## 1. 基础设施

### 1.1 MySQL 8.0（必需）

```powershell
# Windows 本地启动（用 sakana013 密码）
net start MySQL80   # 或服务管理器启动

# 验证
mysql -uroot -psakana013 -e "SELECT 1"
```

数据库初始化（项目根目录下 `docs/cleanup_and_seed.sql`）：

```powershell
mysql -uroot -psakana013 < docs/cleanup_and_seed.sql
```

预期产出：`del_user_db` / `del_product_db` / `del_order_db` / `del_payment_db` / `del_admin_db` / `del_stats_db` / `del_message_db` 共 7 个 schema。

### 1.2 Redis 7.x（必需）

```powershell
# Docker 方式
docker run -d --name del-redis -p 6379:6379 redis:7-alpine

# 或 Windows 本地服务
net start Redis

# 验证
redis-cli ping   # PONG
```

### 1.3 RabbitMQ 3.x（必需）

```powershell
docker run -d --name del-rabbitmq -p 5672:5672 -p 15672:15672 `
    -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=123456 `
    rabbitmq:3-management

# 验证：浏览器打开 http://127.0.0.1:15672，admin/123456 登录
```

### 1.4 Nacos 2.3.x（必需）

```powershell
docker run -d --name del-nacos -p 8848:8848 -p 9848:9848 `
    -e MODE=standalone -e JVM_XMS=512m -e JVM_XMX=512m `
    nacos/nacos-server:v2.3.2

# 验证：浏览器打开 http://127.0.0.1:8848/nacos，nacos/nacos 登录
```

**Namespace 创建**：登录 Nacos 后 → 命名空间 → 新建 → 命名空间 ID 填 `sakana`。

### 1.5 Elasticsearch 8.x（可选；用于搜索场景）

```powershell
docker run -d --name del-es -p 9200:9200 -e "discovery.type=single-node" `
    -e "xpack.security.enabled=false" -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" `
    docker.elastic.co/elasticsearch/elasticsearch:8.13.0

# 验证
curl http://127.0.0.1:9200   # 集群健康 green/yellow
```

### 1.6 MinIO（必需，文件上传）

```powershell
docker run -d --name del-minio -p 9000:9000 -p 9001:9001 `
    -e MINIO_ROOT_USER=admin -e MINIO_ROOT_PASSWORD=sakana013 `
    minio/minio server /data --console-address ":9001"

# 验证：浏览器打开 http://127.0.0.1:9001，admin/sakana013
# 创建 bucket：product-picture / res
```

## 2. Nacos 配置同步

项目根目录 `nacos-config/DEFAULT_GROUP/` 下的所有 `.yml` / `.json` 文件需要导入 Nacos：

```powershell
# 方式 A：批量导入脚本（推荐）
# 用 nacos-config/nacos_config_export_20260908123757/ 下的 export 文件恢复
# 或用 nacos 1.x/2.x 的 OpenAPI 批量 POST

# 方式 B：手动逐个导入
# 进入 Nacos 控制台 → 配置管理 → 配置列表 → sakana namespace →
#   选 "+" 导入 → Data ID 填 del-common-jwt.yml 等文件名，Group 填 DEFAULT_GROUP，
#   格式选 YAML/JSON，把项目里同名文件内容粘贴进去

# 至少需要：
#   common-jwt.yml / common-jackson.yml / common-redis.yml / common-amqp.yml
#   common-mybatis.yml / common-actuator.yml / common-swagger.yml
#   del-user.yml / del-product.yml / del-order.yml / del-payment.yml
#   del-cs.yml / del-cs-prompt.yml / del-gateway.yml
#   sentinel-flow-rules-delproduct.json / sentinel-degrade-rules-delproduct.json
```

**简化**：直接 docker run 一个临时 nacos，挂载 `nacos-config/` 目录作为初始化卷（需 Nacos 2.2+ 支持）。

## 3. 各服务启动顺序

按依赖顺序启动（端口配置来自 `nacos-config/.../*-port*.yml` 或各模块 application.yml）：

| # | 服务 | 端口 | 启动命令 |
|---|---|---|---|
| 1 | del-gateway | 9800 | `mvn -pl del-gateway spring-boot:run` |
| 2 | del-user | 10001 | `mvn -pl del-user spring-boot:run` |
| 3 | del-product | 10000 | `mvn -pl del-product spring-boot:run` |
| 4 | del-order | 10002 | `mvn -pl del-order spring-boot:run` |
| 5 | del-payment | 10003 | `mvn -pl del-payment spring-boot:run` |
| 6 | del-stats | 10004 | `mvn -pl del-stats spring-boot:run` |
| 7 | del-message | 10005 | `mvn -pl del-message spring-boot:run` |
| 8 | del-cs | 10011 | `mvn -pl del-cs spring-boot:run` |
| 9 | del-admin | 10006 | `mvn -pl del-admin spring-boot:run` |

**注意**：del-cs 需要 `DASHSCOPE_API_KEY` 环境变量指向通义千问 API key，否则聊天会失败（搜索/网关测试不受影响）。

```powershell
# 一键启动全部（PowerShell，10 个窗口）
$modules = @(''del-gateway'',''del-user'',''del-product'',''del-order'',''del-payment'',''del-stats'',''del-message'',''del-cs'',''del-admin'')
foreach ($m in $modules) {
    Start-Process powershell -ArgumentList "-NoExit","-Command","cd E:\Idea_project\delivery-cloud; mvn -pl $m spring-boot:run"
    Start-Sleep 5
}
```

## 4. 健康检查

```powershell
# 网关
curl http://127.0.0.1:9800/actuator/health
# 应返回 {"status":"UP"}

# 各服务直连健康端点
curl http://127.0.0.1:10001/actuator/health   # del-user
curl http://127.0.0.1:10000/actuator/health   # del-product
curl http://127.0.0.1:10002/actuator/health   # del-order
curl http://127.0.0.1:10003/actuator/health   # del-payment

# del-cs 自定义健康端点
curl http://127.0.0.1:10011/api/v1/cs/health
# 应返回 {"status":"UP","service":"del-cs","mode":"langchain4j"}
```

## 5. Token 生成

```powershell
cd E:\Idea_project\delivery-cloud\docs\perf\scripts

# 1000 个 USER 角色 token（用户ID 1~1000）
python generate_tokens.py --count 1000 --output ..\jmx\tokens.csv

# 100 个 ADMIN 角色 token（用于管理后台测试）
python generate_tokens.py --count 100 --role ADMIN --output ..\jmx\admin-tokens.csv
```

**校验**：把生成的 token 复制到 https://jwt.io 解码，应看到 `sub` / `username` / `role` / `iat` / `exp` 字段正确。

## 6. JMeter 启动

```powershell
# 设置 JMeter 环境变量（可选，避免每次都写全路径）
$env:JMETER_HOME = "E:\software\JMeter\apache-jmeter-5.6.3"
$env:PATH += ";$env:JMETER_HOME\bin"

# 验证
jmeter --version   # 应输出 5.6.3
```

## 7. 端口清单（压测时确认服务可达）

| 端口 | 服务 |
|---|---|
| 9800 | del-gateway（C 端入口） |
| 10000 | del-product（搜索/商品） |
| 10001 | del-user（用户/认证） |
| 10002 | del-order（订单） |
| 10003 | del-payment（支付/免单） |
| 6379 | Redis |
| 5672 | RabbitMQ |
| 8848 | Nacos |
| 9000 | MinIO |
| 9200 | Elasticsearch（可选） |

## 8. 故障排查速查表

| 现象 | 原因 | 解决 |
|---|---|---|
| `Connection refused: 127.0.0.1:8848` | Nacos 未启动或 namespace 未建 | 启动 Nacos + 创建 `sakana` namespace |
| `JWT invalid: Token 已过期` | 脚本中 `--ttl` 太短 | 重新生成 token |
| `NPE in SearchServiceImpl.search` | ES 未启动，ObjectProvider 拿到 null | 启动 ES 或注入 mock |
| 抢免单全部返回 "QUOTA_EXHAUSTED" | 没有可用免单码 | 先用 admin 接口 `POST /api/v1/admin/free-orders/{id}/publish` 发布活动 |
| JMeter CSV 找不到文件 | 工作目录不对 | `cd docs\perf\jmx` 再运行 JMeter |

## 9. 一键启动脚本（待完善）

未来可以写一个 `scripts/start-all.ps1`：

```powershell
# 启动所有基础设施
docker compose up -d   # 假设有 docker-compose.yml

# 等待基础设施就绪
Start-Sleep 30

# 同步 Nacos 配置
.\scripts\sync-nacos-config.ps1

# 启动各服务
.\scripts\start-services.ps1

# 等待服务就绪
.\scripts\wait-for-services.ps1

Write-Host "环境就绪，开始压测" -ForegroundColor Green
```

文档标记为 "待完善"，如需可继续推进。