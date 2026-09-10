# 📋 工单 #P2-003：网关路由动态刷新（避免重启 del-gateway）

> **创建时间**：2026-09-04
> **优先级**：P2（运维体验优化，非阻塞）
> **接收方**：后端工程师
> **影响服务**：del-gateway
> **预计工时**：4 小时
> **依赖工单**：无
> **代码净变更**：+30 行配置 + +20 行 Java

---

## 一、问题描述

### 1.1 当前痛点

`del-gateway.yml` 修改后，**必须重启 del-gateway 进程**才能让路由生效：

```bash
# 当前的运维流程
1. 修改 Nacos 上 del-gateway.yml
2. SSH 到 gateway 服务器
3. kill -9 gateway 进程
4. 重启 java -jar del-gateway.jar
5. 等待 30-60s
6. 期间所有请求都会 502

# 问题：
- ❌ 修改一条路由要重启服务
- ❌ 重启期间所有 C/B 端请求失败
- ❌ 凌晨紧急修复路由要半夜重启
```

### 1.2 Spring Cloud Gateway 的能力

Spring Cloud Gateway 默认支持 Nacos 配置动态刷新，但需要正确配置：

```yaml
spring:
  cloud:
    nacos:
      config:
        refresh-enabled: true
    gateway:
      # 默认会监听 spring.cloud.gateway 配置变化
```

### 1.3 目标

修改路由配置后，**自动** 1-3 秒内生效，不需要重启 gateway。

---

## 二、修复方案

### 2.1 启用 Spring Cloud Gateway 动态路由

#### 改动 1：del-gateway 配置

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: sakana
        group: DEFAULT_GROUP
        file-extension: yml
        # ★ 新增：开启自动刷新
        refresh-enabled: true
    gateway:
      # ★ 新增：开启动态路由（从 Nacos 加载）
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

#### 改动 2：使用 Nacos DataId 方式管理路由

把路由配置从 `del-gateway.yml` 拆出来，独立成 `del-gateway-routes.yml`：

```yaml
# Nacos DataId: del-gateway-routes.yml
# Group: DEFAULT_GROUP
spring:
  cloud:
    gateway:
      routes:
        - id: del-product-vote
          uri: lb://del-product
          predicates:
            - Path=/api/v1/orders/*/items/*/vote
          filters:
            - StripPrefix=0
        # ... 其他路由 ...
```

#### 改动 3：Java 端加 RefreshScope

```java
@RestController
@RefreshScope  // ★ 新增：配置刷新时重新加载
public class DynamicRouteController {
    
    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;
    
    /**
     * 通过 Nacos 配置变更触发路由刷新
     */
    @NacosConfigListener(dataId = "del-gateway-routes.yml", groupId = "DEFAULT_GROUP")
    public void onRoutesChange(String newRoutes) {
        // 解析新路由，写入 RouteDefinitionWriter
        // Spring Cloud Gateway 自动应用
    }
}
```

### 2.2 方案 A（最小改动）：启用 Spring 原生动态路由

Spring Cloud Gateway 在 2.x 版本后，**默认支持**从配置中心加载路由。只需确保：

1. `spring.cloud.gateway.discovery.locator.enabled = true`
2. Nacos 配置开启 refresh

#### 具体改动

```yaml
# del-gateway application.yml
spring:
  cloud:
    nacos:
      config:
        refresh-enabled: true   # ★ 新增
    gateway:
      discovery:
        locator:
          enabled: true          # ★ 新增
```

**优点**：几乎 0 行 Java 代码改动
**缺点**：需要验证是否真的生效

### 2.3 方案 B（完整方案）：自定义 Nacos 监听器

如果方案 A 不生效，需要手写 Nacos 监听器监听 `del-gateway.yml` 变更。

---

## 三、推荐方案

**先用方案 A 测试**，如果 Spring Cloud Gateway 不自动刷新再升级到方案 B。

---

## 四、验证清单

### 4.1 编译与启动

- [ ] del-gateway 编译通过
- [ ] 重启 del-gateway
- [ ] 启动日志确认路由加载数量

### 4.2 动态刷新验证

1. 在 Nacos 控制台给 `del-gateway.yml` 加一条新路由：
   ```yaml
   - id: test-route
     uri: lb://del-product
     predicates:
       - Path=/test/dynamic
     filters:
       - StripPrefix=0
   ```
2. 发布配置
3. **不需要重启 del-gateway**
4. 等 5-10 秒
5. `curl http://localhost:10010/test/dynamic`
6. 预期：200 OK（或转发到 del-product 的某个端点）

### 4.3 回滚验证

把测试路由从 Nacos 删除 → 5-10 秒内自动失效。

### 4.4 不回归验证

- [ ] 所有现有路由仍正常工作
- [ ] vote 路由仍精确匹配（#ARCH-FEIGN-001）
- [ ] 现有 9 种 vote 场景仍正常

---

## 五、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 动态刷新不生效 | 🟡 中 | 方案 A 不行时升级到方案 B |
| 路由解析失败 | 🟡 中 | 启动时校验路由合法性 |
| 配置推送延迟 | 🟢 低 | Nacos 默认秒级 |
| 多个 gateway 实例配置不同步 | 🟢 低 | Nacos 保证一致性 |

---

## 六、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待填_ | |
| 动态刷新验证 | _待填_ | |