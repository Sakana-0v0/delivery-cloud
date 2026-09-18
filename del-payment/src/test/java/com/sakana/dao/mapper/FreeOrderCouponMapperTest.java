package com.sakana.dao.mapper;

import com.sakana.dao.entity.FreeOrderCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FreeOrderCouponMapper SQL 契约测试 - 验证 ★ P1-批量预热的 SQL 语法。
 *
 * <p>不连接数据库，直接断言：
 * <ul>
 *   <li>batchInsert 方法存在且签名是 (List&lt;FreeOrderCoupon&gt;) -&gt; int</li>
 *   <li>@Insert SQL 包含多值 INSERT 语法（foreach 循环 + VALUES (...),(...),...）</li>
 *   <li>SQL 引用我们 SELECT 当前列：activity_id / code / max_amount / status / created_at / updated_at</li>
 *   <li>atomicGrab 仍强制 WHERE status='AVAILABLE'</li>
 *   <li>atomicUse 仍强制 WHERE status='GRABBED' AND grabbed_user_id=?</li>
 * </ul>
 */
@DisplayName("FreeOrderCouponMapper SQL 契约测试")
class FreeOrderCouponMapperTest {

    @Test
    @DisplayName("Mapper 接口声明 @Mapper")
    void testMapperAnnotation() {
        assertNotNull(FreeOrderCouponMapper.class.getAnnotation(Mapper.class),
                "FreeOrderCouponMapper 必须标注 @Mapper 以便 Spring 扫描");
    }

    @Test
    @DisplayName("batchInsert 方法签名：List<FreeOrderCoupon> -> int")
    void testBatchInsertSignature() throws NoSuchMethodException {
        Method method = FreeOrderCouponMapper.class.getMethod("batchInsert", List.class);
        assertEquals(int.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        Parameter param = method.getParameters()[0];
        assertEquals(List.class, param.getType());
        // 形参应为 @Param("list") List<FreeOrderCoupon>
        assertNotNull(param.getAnnotation(org.apache.ibatis.annotations.Param.class));
        assertEquals("list", param.getAnnotation(org.apache.ibatis.annotations.Param.class).value());
    }

    @Test
    @DisplayName("batchInsert @Insert SQL 含多值 INSERT + foreach 语法")
    void testBatchInsert_sqlStructure() throws NoSuchMethodException {
        Method method = FreeOrderCouponMapper.class.getMethod("batchInsert", List.class);
        Insert annotation = method.getAnnotation(Insert.class);

        assertNotNull(annotation, "batchInsert 必须有 @Insert 注解");
        String[] sqlLines = annotation.value();
        String fullSql = String.join("\n", sqlLines);

        assertTrue(fullSql.contains("INSERT INTO t_free_order_coupon"),
                "SQL 必须插入到 t_free_order_coupon 表");
        assertTrue(fullSql.contains("<script>") && fullSql.contains("</script>"),
                "SQL 必须用 <script> 包裹以支持 <foreach>");
        assertTrue(fullSql.contains("<foreach collection='list'"),
                "SQL 必须循环 list 集合");
        assertTrue(fullSql.contains("VALUES"),
                "SQL 必须用 VALUES 子句（多值 INSERT）");
        assertTrue(fullSql.contains("separator=','"),
                "每个 value tuple 之间用逗号分隔");
    }

    @Test
    @DisplayName("batchInsert SQL 引用所有 4 个必要列 + 2 个时间戳")
    void testBatchInsert_columns() throws NoSuchMethodException {
        Method method = FreeOrderCouponMapper.class.getMethod("batchInsert", List.class);
        String sql = String.join("\n", method.getAnnotation(Insert.class).value());

        // 列名
        assertTrue(sql.contains("activity_id"));
        assertTrue(sql.contains("code"));
        assertTrue(sql.contains("max_amount"));
        assertTrue(sql.contains("status"));
        // 时间戳
        assertTrue(sql.contains("created_at"));
        assertTrue(sql.contains("updated_at"));
        assertTrue(sql.contains("NOW(6)"), "批量插入时硬编 NOW(6) 避免 MyBatis-Plus 自动填充时机问题");
    }

    @Test
    @DisplayName("atomicGrab SQL：CAS 乐观锁 WHERE status='AVAILABLE'")
    void testAtomicGrab_sql() throws NoSuchMethodException {
        Method method = FreeOrderCouponMapper.class.getMethod("atomicGrab", Long.class, Long.class);
        Update annotation = method.getAnnotation(Update.class);

        assertNotNull(annotation);
        String sql = String.join("\n", annotation.value());
        assertTrue(sql.contains("UPDATE t_free_order_coupon"));
        assertTrue(sql.contains("SET status = 'GRABBED'"));
        assertTrue(sql.contains("WHERE id = #{id} AND status = 'AVAILABLE'"),
                "atomicGrab 必须强制 status='AVAILABLE' 条件更新");
    }

    @Test
    @DisplayName("atomicUse SQL：CAS WHERE status='GRABBED' AND grabbed_user_id=?")
    void testAtomicUse_sql() throws NoSuchMethodException {
        Method method = FreeOrderCouponMapper.class.getMethod("atomicUse", String.class, Long.class, Long.class);
        Update annotation = method.getAnnotation(Update.class);

        assertNotNull(annotation);
        String sql = String.join("\n", annotation.value());
        assertTrue(sql.contains("UPDATE t_free_order_coupon"));
        assertTrue(sql.contains("SET status = 'USED'"));
        assertTrue(sql.contains("WHERE code = #{code} AND status = 'GRABBED' AND grabbed_user_id = #{userId}"),
                "atomicUse 必须三重条件：code + status + grabbed_user_id");
    }

    @Test
    @DisplayName("FreeOrderCouponStatus 状态机枚举完整性")
    void testCouponStatusEnum() {
        // 验证 4 个状态都存在
        List<String> codes = Arrays.asList("AVAILABLE", "GRABBED", "USED", "EXPIRED");
        for (String c : codes) {
            boolean found = false;
            for (com.sakana.enums.FreeOrderCouponStatus s : com.sakana.enums.FreeOrderCouponStatus.values()) {
                if (s.code().equals(c)) { found = true; break; }
            }
            assertTrue(found, "FreeOrderCouponStatus 应包含 " + c);
        }
    }
}
