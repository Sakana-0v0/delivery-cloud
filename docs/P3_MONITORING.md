# P3 监控 / 异步线程池（#P3-MONITORING）

> **关联**：把 ★ P0-Sentinel 双读从被动降级升级到可观测；把 ★ P1-批量预热结果钉到 Grafana。
> **Jira/Issue**：后续由你维护
> **Commit**：（待 P3 提交后回填）

---

## 1. 新增组件

| 文件 | 模块 | 作用 |
|---|---|---|
| `del-product/.../configs/AsyncConfig.java` | del-product | 命名线程池（`taskExecutor`/`indexTaskExecutor`），优雅停机 |
| `del-product/.../metrics/SearchMetrics.java` | del-product | 菜品搜索业务指标注册（Counter + Timer） |
| `del-payment/.../metrics/CouponMetrics.java` | del-payment | 抢免单 + 批量预热业务指标 |
| `del-product/.../search/service/impl/SearchServiceImpl.java` | del-product | @SentinelResource + SearchMetrics 埋点 |
| `del-product/.../search/service/impl/IndexingServiceImpl.java` | del-product | @Async(`indexTaskExecutor`) + 重建耗时 |
| `del-payment/.../services/impl/FreeOrderGrabServiceImpl.java` | del-payment | doGrab 路径埋点 success/error |
| `del-payment/.../services/impl/FreeOrderActivityServiceImpl.java` | del-payment | publishActivity 批量预热 Timer |
| `del-product/pom.xml` | del-product | 新增 `micrometer-registry-prometheus` 依赖 |
| `docs/perf/grafana-dashboard.json` | 文档 | 9 个 panel 的 Grafana 仪表板（PromQL） |

---

## 2. 指标清单

### 2.1 业务 Counter

| 指标名 | 标签 | 含义 |
|---|---|---|
| `dish.search.calls` | `outcome={hit,empty,fallback,block,error}` | 菜品搜索每次调用的结果分类 |
| `coupon.grab.attempts` | `outcome={success,already_grabbed,quota_exhausted,duplicate_key,not_available,not_found}` | 抢免单每次请求的业务结果 |

### 2.2 业务 Timer（自动暴露 percentileHistogram）

| 指标名 | 单位 | 含义 |
|---|---|---|
| `dish.search.latency` | seconds | 搜索端到端延迟（含 ES 查询 + 业务组装） |
| `coupon.grab.latency` | seconds | 抢免单端到端延迟 |
| `coupon.warmup.duration` | seconds | 批量预热总耗时 |
| `dish.index.rebuild.duration` | seconds | ES dish 索引全量重建耗时 |

### 2.3 系统指标（Spring Boot + Prometheus registry 自动暴露）

| 指标名 | 含义 |
|---|---|
| `executor_active_threads{name="applicationTaskExecutor"}` | 异步线程池活跃线程数 |
| `executor_pool_size` | 当前线程池大小 |
| `executor_queue_size` | 队列堆积任务数 |
| `jvm_memory_used_bytes{area="heap"}` | JVM 堆内存使用 |
| `process_cpu_usage` | 进程 CPU 占用 |

---

## 3. 端点

```bash
# Prometheus 抓取端点（Spring Boot 默认）
curl http://127.0.0.1:10010/actuator/prometheus

# 指标查询端点（人用）
curl http://127.0.0.1:10010/actuator/metrics/dish.search.calls
curl 'http://127.0.0.1:10010/actuator/metrics/dish.search.calls?tag=outcome:block'
```

Nacos 配置 `common-actuator.yml` 已暴露 `prometheus` 端点，无需额外配置。

---

## 4. Grafana 仪表板

`docs/perf/grafana-dashboard.json` 包含 9 个 panel：

1. **菜品搜索 RPS (by outcome)** — time series
2. **菜品搜索 P50/P95/P99 (ms)** — time series
3. **搜索降级率** — stat (block+fallback+error / total)
4. **抢免单 RPS (by outcome)** — time series
5. **抢免单 P95/P99 (ms)** — time series
6. **免单券抢空速率 (quota_exhausted/min)** — stat
7. **最近一次免单预热耗时 (s)** — stat
8. **索引重建耗时 (s)** — stat
9. **异步线程池指标** — time series (active/pool_size/queue_size)

导入方式：
1. Grafana → "+" → Import Dashboard
2. 上传 `docs/perf/grafana-dashboard.json`
3. 选择 Prometheus 数据源
4. 选择 application 变量（默认 `$application`）

---

## 5. 关键 PromQL 示例

```promql
# 搜索降级率（>30s）
sum(rate(dish_search_calls_total{outcome=~"block|fallback|error"}[5m]))
/
sum(rate(dish_search_calls_total[5m]))

# 抢免单 P99 延迟（5m 内）
histogram_quantile(0.99, sum(rate(coupon_grab_latency_seconds_bucket[5m])) by (le))

# Sentinel 熔断/限流触发速率
sum(rate(dish_search_calls_total{outcome="block"}[1m]))

# 异常率突增告警（5m 内 > 10%）
sum(rate(coupon_grab_attempts_total{outcome!="success"}[5m]))
/
sum(rate(coupon_grab_attempts_total[5m])) > 0.10

# 异步线程池告警（队列堆积 > 50）
executor_queue_size > 50
```

---

## 6. 待办（修复后回填实际数字）

- [ ] 在生产集群验证 `/actuator/prometheus` 真正暴露 `dish_search_*` / `coupon_grab_*` 指标
- [ ] Grafana 接入 Prometheus 数据源后导入 `dashboard.json`
- [ ] 配置告警：搜索降级率 > 20% 持续 5m → 企业微信/钉钉
- [ ] 配置告警：coupon_grab.quota_exhausted 速率激增 → 库存告警
- [ ] JVM heap 占用 > 80% → Prometheus alertmanager
- [ ] 整理 alertmanager 规则文件 `docs/P3_MONITORING_alerts.yml`

---

## 7. 验证

```bash
# 跑 metrics 单测（不需要服务起来）
mvn -pl del-product -am test -Dtest=SearchMetricsTest -Dsurefire.failIfNoSpecifiedTests=false -o
mvn -pl del-payment -am test -Dtest=CouponMetricsTest -Dsurefire.failIfNoSpecifiedTests=false -o

# 期望输出
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0   (SearchMetricsTest)
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0   (CouponMetricsTest)
```

启动 del-product 后访问 `http://127.0.0.1:10010/actuator/prometheus` 应能看到：
- `dish_search_calls_total{outcome="hit",...}`
- `dish_search_calls_total{outcome="fallback",...}`
- `dish_search_latency_seconds_bucket{...}`

启动 del-payment 后应能看到：
- `coupon_grab_attempts_total{outcome="success",...}`
- `coupon_grab_attempts_total{outcome="already_grabbed",...}`
- `coupon_warmup_duration_seconds_sum`
- `coupon_warmup_batches_total`