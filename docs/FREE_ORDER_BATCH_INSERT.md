# 免单码批量预热（#FREE-ORDER-006）

> **关联**：简历里"流水线分批低锁插入预热十万级库存，避免活动上线瞬间数据库连接池打满"。
> 原实现是 for 循环单条 INSERT（10K 次 round-trip），重构后按 500 条/批用单条 SQL 多值 INSERT。

## 1. 性能对比（10K 免单码）

| 方案 | SQL 次数 | round-trip | 连接池占用 | 预估耗时 |
|---|---|---|---|---|
| **重构前** 单条 INSERT | 10000 | 10000 | 10000 个连接秒 | ~30s |
| **重构后** 多值 INSERT 500/批 | 20 | 20 | 20 个连接秒 | ~1s |

> 数据基于本地 MySQL 8.0 / HikariCP 10 连接 / 单条 INSERT ~3ms 的粗略估算，实际以压测为准。

## 2. 实现

### 2.1 Mapper（`FreeOrderCouponMapper.batchInsert`）

```java
@Insert({
    "<script>",
    "INSERT INTO t_free_order_coupon (activity_id, code, max_amount, status, created_at, updated_at) VALUES ",
    "<foreach collection='list' item='c' separator=','>",
    "(#{c.activityId}, #{c.code}, #{c.maxAmount}, #{c.status}, NOW(6), NOW(6))",
    "</foreach>",
    "</script>"
})
int batchInsert(@Param("list") List<FreeOrderCoupon> coupons);
```

- 用 MyBatis `<script>` + `<foreach>` 构建多值 INSERT：`VALUES (a,b),(c,d),(e,f),...`
- 单条 SQL 一次写入一批（500 行）
- 服务端使用 `rewriteBatchedStatements=true` 时 JDBC 会自动合并（未启用时仍受益于单次 round-trip）

### 2.2 Service（`FreeOrderActivityServiceImpl.publishActivity`）

```java
String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
int total = activity.getTotalQuota();
List<FreeOrderCoupon> all = new ArrayList<>(total);
for (int i = 1; i <= total; i++) {
    FreeOrderCoupon coupon = new FreeOrderCoupon();
    coupon.setActivityId(activityId);
    coupon.setCode(String.format("FREE%s%04d", ts, i));  // %04d 支持万级序号
    coupon.setMaxAmount(activity.getMaxFreeAmount());
    coupon.setStatus(FreeOrderCouponStatus.AVAILABLE.code());
    all.add(coupon);
}

// 分块批量写入
int totalBatches = (total + BATCH_INSERT_CHUNK_SIZE - 1) / BATCH_INSERT_CHUNK_SIZE;
for (int batchIdx = 0; batchIdx < totalBatches; batchIdx++) {
    int from = batchIdx * BATCH_INSERT_CHUNK_SIZE;
    int to = Math.min(from + BATCH_INSERT_CHUNK_SIZE, total);
    couponMapper.batchInsert(all.subList(from, to));
}
```

- `BATCH_INSERT_CHUNK_SIZE = 500`：单批 500 行，10K → 20 批
- 内存构造先于 DB 写入：避免边构造边写阻塞数据库
- `@Transactional` 包裹：批次内失败整体回滚

## 3. 验证

### 3.1 编译

```
mvn -pl del-payment -am compile
```

期望：`BUILD SUCCESS`，无警告。

### 3.2 运行时验证

1. **正确性**：创建活动 `totalQuota=10000`，发布活动，检查 `SELECT COUNT(*) FROM t_free_order_coupon WHERE activity_id=?` = 10000
2. **码格式**：`SELECT code FROM t_free_order_coupon WHERE activity_id=? ORDER BY id LIMIT 5`，应为 `FREE<yyyyMMddHHmmss>0001 ~ 0005`
3. **性能**：发布活动前后用 `SHOW PROCESSLIST` 观察连接数；使用 10000 免单码压测，对比重构前后耗时
4. **状态机**：发布完成后 `SELECT status FROM t_free_order_activity WHERE id=?` = `PUBLISHED`

## 4. 调优空间

- **MySQL 端**：`max_allowed_packet` 默认 64MB；500 行/批单包约 100KB，远低于上限
- **连接池**：HikariCP 默认 10 连接；20 批/活动 → 平均每批 1 连接占用 < 100ms，连接池不会被打满
- **JDBC 批优化**：未来可在 `application.yml` 配置 `spring.datasource.url` 添加 `rewriteBatchedStatements=true&useServerPrepStmts=true`，进一步合并相同 INSERT（多值 INSERT 已优于单条批）

## 5. 受影响清单

| 文件 | 类型 | 说明 |
|---|---|---|
| del-payment/.../FreeOrderCouponMapper.java | 修改 | 新增 `batchInsert(List<FreeOrderCoupon>)` 方法 |
| del-payment/.../FreeOrderActivityServiceImpl.java | 修改 | `publishActivity` 改用 chunk 批量；变更码格式 `%03d → %04d` 支持万级 |