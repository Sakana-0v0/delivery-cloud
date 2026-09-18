# Performance Test Scripts (JMeter 5.6.3)

> 用于在简历的量化指标占位符 `[X]/[Y]` 上填真实数据。
> 所有脚本使用 `__P()` 暴露参数，可通过 `-Jkey=value` 覆盖默认值。

## 0. 环境前置

- **JMeter**：5.6.3 已装在 `E:\software\JMeter\apache-jmeter-5.6.3\bin\jmeter.bat`
- **Java**：JDK 17+（`java -version`）
- **服务**：del-gateway / del-product / del-payment / del-user 全部启动
- **基础设施**：MySQL、Redis、RabbitMQ 全部启动

## 1. 数据准备

### 1.1 生成 token (tokens.csv)

每个并发线程需要一个不同 userId 的 JWT。两个方案：

**方案 A：用 del-user 的注册接口 + 登录接口脚本化生成**

```powershell
$tokens = @()
1..1000 | ForEach-Object {
    $username = "perf_user_$_"
    $email = "perf_$_@test.com"
    # 调用 /api/v1/auth/register 注册（del-user 接口）
    # 调用 /api/v1/auth/login 拿 token
    # 把 userId,token 追加到 tokens.csv
}
```

**方案 B：用脚本生成 JWT（推荐，无需启动业务）**

参照 `scripts/generate-jwt.ps1`：用 `app.jwt.user-pool-secret` 配置的密钥，构造有效 JWT。
这是离线方式，速度快、确定性高。

### 1.2 准备搜索 query 词表 (queries.csv)

```csv
query
清淡
少油
番茄
素食
...
```

至少 100 条，覆盖长尾词（清淡/少油/低卡/无糖 等）。可以从 `del-product` 现有商品名里抽。

## 2. 运行

### 2.1 抢免单（#FREE-ORDER-006）

```
cd E:\Idea_project\delivery-cloud\docs\perf\jmx
..\..\..\..\..\..\E:\software\JMeter\apache-jmeter-5.6.3\bin\jmeter.bat ^
    -n -t seckill-grab.jmx ^
    -Jhost=127.0.0.1 -Jport=9800 ^
    -JactivityId=1 -Jthreads=1000 -JrampUp=10 -Jloops=1 ^
    -l results/seckill-grab.jtl
```

**关键指标**：
- `Throughput` ≥ 800 RPS (claim: 99.8% success)
- 99% RT < 200ms
- `Sum of failures = 0` （无超卖）

**断言**：`SELECT COUNT(*) FROM t_free_order_coupon WHERE activity_id=1 AND status IN ('GRABBED','USED')` 应等于 1000。

### 2.2 搜索吞吐量（#SEARCH-SENTINEL）

```
jmeter.bat -n -t search-throughput.jmx ^
    -Jhost=127.0.0.1 -Jport=9800 ^
    -Jthreads=200 -Jloops=5 ^
    -l results/search-throughput.jtl
```

**关键指标**：
- Throughput 达到 2000 RPS
- P99 RT < 500ms (ES 正常) / < 200ms (降级 MySQL)
- 模拟 ES 故障时观察降级是否触发 + 切换时间

**模拟 ES 故障**：杀掉 ES 容器，再跑一次，对比降级路径。

### 2.3 评价缓存（#REVIEW-CACHE）

```
jmeter.bat -n -t review-cache.jmx ^
    -JorderId=123 -JproductId=abc-001 ^
    -Jthreads=100 -Jloops=5 ^
    -l results/review-cache.jtl
```

**对比实验**：
1. 先停 del-product → 清空 Caffeine → 跑脚本，记 MySQL QPS（应该是高）
2. 再启动 Caffeine+Redis → 跑脚本 → MySQL QPS 应大幅下降（target -70%）

**指标采集**：用 `SHOW GLOBAL STATUS LIKE ''Com_select''` 前后差值估算 MySQL QPS。

### 2.4 网关鉴权（#GATEWAY-AUTH）

```
jmeter.bat -n -t gateway-auth.jmx ^
    -Jhost=127.0.0.1 -Jport=9800 ^
    -Jthreads=200 -Jloops=5 ^
    -l results/gateway-auth.jtl
```

**关键指标**：
- 2000 RPS 持续
- P99 RT < 50ms（仅鉴签 + 业务首字节）

## 3. 结果汇总

把 4 个脚本的 Summary Report 截图 + 关键数字汇总到 `docs/perf/results/` 下，建议文件命名：
- `seckill-grab-summary.png` (Summary Report)
- `seckill-grab-claim.md` (含 99.8% 等量化指标)

## 4. 已知限制

- **SSE 长连接**：JMeter 默认不擅长测 SSE（本项目 del-cs），可在 JMeter 中用 WebSocket Sampler 替代
- **分布式压测**：单机 JMeter 2000 RPS 是极限；更大压力需要用 JMeter 集群模式 (`-R slave1,slave2 -D java.rmi.server.hostname=...`)
- **DB 隔离**：压测前建议把生产数据隔离到独立 schema

## 5. 文件清单

| 文件 | 用途 |
|---|---|
| `jmx/seckill-grab.jmx` | 抢免单 1000 并发 |
| `jmx/search-throughput.jmx` | 搜索 2000 RPS |
| `jmx/review-cache.jmx` | 评价计数缓存 100 线程 |
| `jmx/gateway-auth.jmx` | 网关鉴权 2000 RPS |
| `results/` | 报告输出目录（gitignored） |