# 单元测试（JUnit5 + Mockito）

> **关联**：覆盖 ★ P0/P1 三大重构的核心不变量，防回归。

## 1. 测试文件清单

| 模块 | 文件 | 测试数 | 覆盖目标 |
|---|---|---|---|
| `del-cs` | `src/test/java/com/sakana/cs/context/ChatContextHolderTest.java` | 8 | ThreadLocal 生命周期、userIdAsLong 解析、set/clear 不变量 |
| `del-cs` | `src/test/java/com/sakana/cs/service/tools/OrderDetailToolTest.java` | 6 | Tool 签名无 userId / @Tool 描述不含 userId / ChatContextHolder 路由 / prompt 注入防 / Feign 异常吞掉 |
| `del-product` | `src/test/java/com/sakana/search/service/impl/SearchServiceImplTest.java` | 8 | @SentinelResource 注解 / blockHandler/fallback 签名 / fallback 复用 / RESOURCE 常量 |
| `del-payment` | `src/test/java/com/sakana/dao/mapper/FreeOrderCouponMapperTest.java` | 7 | batchInsert 多值 INSERT SQL / atomicGrab/Use CAS 条件 / 状态机枚举完整性 |

**合计 29 个测试，全部通过，0 失败 0 错误。**

## 2. 关键测试用例

### 防 prompt 注入越权（OrderDetailTool）
```java
@Test
void testPromptInjection_orderNoCantOverrideUserId() {
    ChatContextHolder.set(new ChatContext("42", "mocked-jwt", now));
    tool.getOrderDetail("ORD-OTHER-USER-9999");
    verify(orderFeign).getByOrderNo(42L, "ORD-OTHER-USER-9999");
    // 即使 LLM 被骗改了 orderNo，userId 永远是 42
}
```

### Sentinel 注解契约（SearchServiceImpl）
```java
@Test
void testSentinelAnnotationPresent() {
    SentinelResource ann = searchMethod.getAnnotation(SentinelResource.class);
    assertEquals("dishSearch", ann.value());
    assertEquals("searchBlockHandler", ann.blockHandler());
    assertEquals("searchFallbackWithEx", ann.fallback());
}
```

### 多值 INSERT SQL 契约（FreeOrderCouponMapper）
```java
@Test
void testBatchInsert_sqlStructure() {
    String sql = String.join("\\n", method.getAnnotation(Insert.class).value());
    assertTrue(sql.contains("<script>") && sql.contains("</script>"));
    assertTrue(sql.contains("<foreach collection='list'"));
    assertTrue(sql.contains("VALUES"));
    assertTrue(sql.contains("separator=','"));
}
```

## 3. 跑测试

```bash
cd E:\Idea_project\delivery-cloud

# 单模块
mvn -pl del-cs -am test -Dtest='ChatContextHolderTest,OrderDetailToolTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -o

mvn -pl del-product -am test -Dtest='SearchServiceImplTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -o

mvn -pl del-payment -am test -Dtest='FreeOrderCouponMapperTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -o

# 全跑
mvn test -o
```

## 4. 已知限制

- `FreeOrderCouponMapperTest` 是 SQL 契约测试（不连真实 DB），验证 @Insert/@Update 注解的 SQL 片段；真实 INSERT/UPDATE 行为需要集成测试覆盖（建议未来加 TestContainers + MySQL）
- `SearchServiceImplTest` 不直接调用 `search()`（ES 链路 mock 太重），改为通过 blockHandler/fallback 的直接调用 + 注解契约；完整业务流仍需 Postman 集成测试
- 用户 WIP 的 `PaymentServiceImplTest.java.disabled`（暂存旧测试，等用户修复构造器签名后再启用）