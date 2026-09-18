# del-product 搜索熔断限流（#SEARCH-SENTINEL）

> **关联**：简历里"Sentinel 双读熔断"声明；原实现是裸 try/catch 切 MySQL，
> 重构后接入 Spring Cloud Alibaba Sentinel 实现真正的限流 + 熔断 + 业务 fallback。

## 1. 架构

```
[SearchController] → SearchService.search(request)
                            │
                  ┌─────────┴─────────┐
                  │ @SentinelResource │
                  │  value=dishSearch │
                  └─────────┬─────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
   search() 正常返回    searchBlockHandler  searchFallbackWithEx
        │              (限流/熔断触发)       (业务异常触发)
        ▼                   │                   │
   ES 结果                   ▼                   ▼
                  复用 searchFallback()  复用 searchFallback()
                            │
                            ▼
                     MySQL LIKE 兜底
```

## 2. 规则

### 2.1 限流规则（`sentinel-flow-rules-delproduct.json`）

```json
[
  {
    "resource": "dishSearch",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

- `resource`: `dishSearch`（与 `@SentinelResource.value` 一致）
- `grade=1`: QPS 限流
- `count=100`: 单实例 100 QPS 上限
- `controlBehavior=0`: 直接拒绝

### 2.2 熔断规则（`sentinel-degrade-rules-delproduct.json`）

```json
[
  {
    "resource": "dishSearch",
    "grade": 0,
    "count": 1500,
    "timeWindow": 30,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "slowRatioThreshold": 0.5
  }
]
```

- `grade=0`: 慢调用比例（RT-based degrade）
- `count=1500`: 慢调用阈值 1500ms
- `slowRatioThreshold=0.5`: 统计窗口内 50% 请求超过 1500ms 即熔断
- `timeWindow=30`: 熔断 30s（半开探测）
- `minRequestAmount=5`: 统计窗口内至少 5 个请求才判定
- `statIntervalMs=10000`: 统计窗口 10s

### 2.3 Nacos datasource

`del-product.yml` 配置：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: 127.0.0.1:8080   # 可选：Sentinel Dashboard
        port: 8719
      datasource:
        flow:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: sakana
            group-id: DEFAULT_GROUP
            data-id: sentinel-flow-rules-delproduct
            rule-type: flow
        degrade:
          nacos:
            server-addr: 127.0.0.1:8848
            namespace: sakana
            group-id: DEFAULT_GROUP
            data-id: sentinel-degrade-rules-delproduct
            rule-type: degrade
```

启动时 Sentinel 从 Nacos 拉规则；Nacos 不可用时使用 `@PostConstruct initDefaultRules()` 装载的本地默认规则。

## 3. 验证

### 3.1 编译验证

```
mvn -pl del-product -am compile
```

期望：`BUILD SUCCESS`，无警告。

### 3.2 运行时验证

- **正常请求**：`GET /api/v1/search?query=xxx`，命中 ES，日志 `[搜索] ES 成功: ...`
- **ES 异常**：`IllegalStateException("ElasticsearchOperations 不可用")` → fallback → `[搜索] 业务异常，降级到 MySQL`，HTTP 200 + MySQL LIKE 结果
- **限流**：JMeter 500 并发打 `/api/v1/search`，观察 QPS 是否被截到 100；超限请求进入 `searchBlockHandler` → MySQL
- **熔断**：用 chaosblade / toxiproxy 把 ES 接口延迟注入到 2000ms，10s 内统计窗口超过 50% 慢调用 → 熔断 30s → 后续请求直接走 MySQL

## 4. 监控与调优

- **Sentinel Dashboard**：docker run -d -p 8080:8080 bladex/sentinel-dashboard
- **指标**：`http://del-product:10000/actuator/sentinel`（如启用 actuator 端点）
- **动态调优**：Nacos 控制台直接修改 JSON 规则，Sentinel 自动热加载

## 5. 受影响清单

| 文件 | 类型 | 说明 |
|---|---|---|
| del-product/pom.xml | 修改 | 新增 `spring-cloud-starter-alibaba-sentinel` 依赖 |
| del-product/.../search/service/impl/SearchServiceImpl.java | 修改 | 添加 `@SentinelResource` + 本地兜底规则 |
| nacos-config/DEFAULT_GROUP/del-product.yml | 修改 | 新增 sentinel transport + Nacos datasource 配置 |
| nacos-config/DEFAULT_GROUP/sentinel-flow-rules-delproduct.json | 新增 | 限流规则 |
| nacos-config/DEFAULT_GROUP/sentinel-degrade-rules-delproduct.json | 新增 | 熔断规则 |

## 6. 注意事项

1. **`@SentinelResource` 要求方法调用通过 Spring 代理**：`SearchController` 注入 `SearchService` 接口，调用 `searchService.search(request)` 时命中 AOP 代理，注解生效；如果改成 `this.search(...)` 会绕过 Sentinel。
2. **blockHandler / fallback 必须与被保护方法在同一个类**，否则 AOP 无法回调。
3. **fallback 与 blockHandler 签名差异**：fallback 最后参数是 `Throwable`，blockHandler 最后参数是 `BlockException`。本实现里两个都复用同一个 `searchFallback(request)` 主体，避免重复逻辑。
4. **`@PostConstruct initDefaultRules()` 装载的是兜底默认规则**；Nacos 规则加载完成后会自动覆盖。如果两者并存，Sentinel 以 Nacos 为准。