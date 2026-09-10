# 部署 Runbook：del-cs 智能客服 MVP（#AI-CS-001-MVP）

> **角色**：运维测试工程师  
> **生效版本**：2026-09-07  
> **关联文档**：`WORK_ORDER_AI-CS-001-MVP.md`、`AI_CS_DESIGN.md`

---

## 一、环境清单

| 项 | 要求 | 当前 |
|----|------|------|
| JDK | 17（Temurin 17.0.19 已验证）| ✅ |
| Nacos | 8848 / namespace=sakana | ✅ |
| Redis | 6379 | ✅（被 del-common 依赖）|
| MySQL | 3306 / del-order_db 等 | ✅ |
| ES | 9200 / dish 索引 | ✅ |
| DashScope API Key | 已配在 del-product application.yml | ✅ |

## 二、运维交付清单（已落地）

### 2.1 Nacos 配置

| Data ID | Group | Namespace | 状态 |
|---------|-------|-----------|------|
| `del-cs-prompt.yml` | DEFAULT_GROUP | sakana | ✅ 已生成待推送 |
| `del-gateway.yml` | DEFAULT_GROUP | sakana | ✅ 已加路由 /api/v1/cs/** |

**文件位置**（Nacos 导出仓库）：
- `nacos-config/nacos_config_export_<时间戳>/DEFAULT_GROUP/del-cs-prompt.yml`
- `nacos-config/nacos_config_export_<时间戳>/DEFAULT_GROUP/del-gateway.yml`

### 2.2 网关路由

```yaml
# 在 del-gateway.yml 的 C 端路由段尾（del-file 之后）
- id: del-cs
  uri: lb://del-cs
  predicates:
    - Path=/api/v1/cs/**
  filters:
    - StripPrefix=0
```

### 2.3 一键验收脚本

脚本：`scripts/verify-ai-cs-mvp.ps1`

```powershell
# 不带 token：跑 Nacos/健康/路由/搜索 4 项检查
powershell -ExecutionPolicy Bypass -File scripts/verify-ai-cs-mvp.ps1

# 带 C 端用户 JWT（登录后从浏览器 localStorage 取）
$token = (Invoke-WebRequest http://localhost:10010/api/v1/auth/login -Method POST ...).token
powershell -ExecutionPolicy Bypass -File scripts/verify-ai-cs-mvp.ps1 -UserToken $token
```

## 四、推送 Nacos 配置

### 方案 A：Nacos 控制台（推荐，单条配置）

1. 打开 `http://127.0.0.1:8848/nacos/`
2. 切到 namespace `sakana`
3. **配置管理 → 配置列表 → `+`**（右上角"+"）
4. Data ID 填 `del-cs-prompt.yml`，Group 选 `DEFAULT_GROUP`，格式 `YAML`
5. 粘贴 `del-cs-prompt.yml` 内容
6. 发布

### 方案 B：批量导入（脚本化）

```powershell
# 用 Nacos OpenAPI 推送（推荐：写成一个 PowerShell 脚本）
$nacos = "http://127.0.0.1:8848"
$ns = "sakana"
$group = "DEFAULT_GROUP"

function Publish-Nacos($dataId, $content) {
   $body = "dataId=$dataId&group=$group&namespaceId=$ns&content=" + [uri]::EscapeDataString($content) + "&type=yaml"
   Invoke-WebRequest "$nacos/nacos/v1/cs/configs" -Method POST -Body $body -ContentType "application/x-www-form-urlencoded" -UseBasicParsing | Out-Null
}

# 推送 del-cs-prompt.yml
$content = Get-Content "nacos-config/nacos_config_export_*/DEFAULT_GROUP/del-cs-prompt.yml" -Raw
Publish-Nacos "del-cs-prompt.yml" $content

# 推送更新后的 del-gateway.yml（含 /api/v1/cs/** 路由）
$gwContent = Get-Content "nacos-config/nacos_config_export_*/DEFAULT_GROUP/del-gateway.yml" -Raw
Publish-Nacos "del-gateway.yml" $gwContent
```

## 五、启动 del-cs

### 5.1 IDEA 启动（推荐 MVP 阶段）

1. 打开 IDEA → 顶部 `del-cs` 工程
2. 右键 `CsApplication.java` → `Run 'CsApplication'`
3. 等到日志 `Started CsApplication in X.XXX seconds`
4. 验证：`curl http://localhost:10011/actuator/health`

### 5.2 命令行启动（备用）

```powershell
cd E:\Idea_project\delivery-cloud\del-cs
mvn spring-boot:run -s ..\.m2\settings.xml
```

> ⚠️ 首次启动要等 langchain4j 拉 DashScope Qwen 模型元数据，可能 10-20 秒冷启动。

## 六、验收清单

### 6.1 后端验证（无 UserToken）

```bash
# 1. Nacos 服务注册
curl http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=del-cs&namespaceId=sakana

# 2. del-cs 健康
curl http://localhost:10011/actuator/health

# 3. 网关路由
curl http://localhost:10010/actuator/gateway/routes | jq .routes[] | select(.routeId=="del-cs")

# 4. 依赖服务（Tool 调用方）
curl 'http://localhost:10010/api/v1/search?query=汤&page=1&size=3'  # del-product
curl http://localhost:10010/api/v1/user/orders -H "Authorization: Bearer $TOKEN"  # del-order
```

### 6.2 SSE 聊天测试（需要 UserToken）

```bash
TOKEN=<登录后从浏览器 localStorage 取>
curl -N -X POST http://localhost:10010/api/v1/cs/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"推荐个清淡的汤"}'
```

**预期 SSE 流**：
```
data: {"type":"token","content":"为"}
data: {"type":"token","content":"您"}
...
data: {"type":"done","content":"...完整回答..."}
```

### 6.3 端到端验收（前端 + 后端）

| 测试 | 预期 | 验证 Tool |
|------|------|-----------|
| 问"推荐清淡的汤" | AI 用 searchDishes，返回 3-5 条清淡菜品 | ✅ searchDishes |
| 问"我最近的订单" | AI 用 getUserOrderHistory，返回最近订单 | ✅ getUserOrderHistory |
| 问"我的订单 ORD-xxx" | AI 用 getOrderDetail | ✅ getOrderDetail |
| 问"我要退款" | AI 说"暂不支持此功能" | ✅ Prompt 限制生效 |
| 问"你是谁" | AI 自我介绍"我是小饿" | ✅ System Prompt 生效 |

## 七、故障排查

| 现象 | 原因 | 处置 |
|------|------|------|
| del-cs 启动报 `ClassNotFoundException` | Maven 没装到本地仓 | 用 IDEA 启动 / `mvn install -pl del-cs -am` |
| `actuator/health` 报 401 | 网关 JWT 校验 | 加 `-H "Authorization: Bearer ..."` 或绕过 |
| SSE 无响应 | Qwen API key 失效 | 查 Nacos `langchain4j.community.dashscope.chat-model.api-key` |
| Tool 调用 404 | 下游服务未启 | 启 del-product / del-order |
| 网关路由未生效 | Nacos 配置未刷新 | 等 30 秒 或 POST `/actuator/refresh` |
| Redis 会话丢失 | TTL 到期 | 默认 30 分钟（设计）|

## 八、回滚预案

```powershell
# 1. 停 del-cs（IDEA 红色停止按钮 / Ctrl+C）

# 2. 改回网关路由（注释掉 del-cs 路由）
#    del-gateway.yml 删除 - id: del-cs ... 那 5 行
#    推回 Nacos

# 3. del-cs-prompt.yml 保留在 Nacos（无副作用）
```

---

**维护人**：运维测试工程师  
**下次回顾**：当 #AI-CS-002（退款 Tool 工单）启动时复审