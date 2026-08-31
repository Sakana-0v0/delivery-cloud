package com.sakana.util;

import com.sakana.configs.JwtProperties;
import com.sakana.filter.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtVerifier 双密钥测试（网关侧）
 *
 * <p>网关的 JwtVerifier 是自实现的（不依赖 del-common 的 DualPoolJwtVerifier），
 * 行为必须一致：user-pool-secret 优先，admin-pool-secret 兜底。
 *
 * <p>由于 JwtVerifier 是 Spring 组件（用 @Value 注入密钥），我们通过反射
 * 直接设置字段来测试，避开 Spring 容器。
 */
class JwtVerifierTest {

    private JwtVerifier verifier;
    private final String USER_SECRET = "test-user-pool-secret-key-must-be-at-least-32-chars-long";
    private final String ADMIN_SECRET = "test-admin-pool-secret-key-must-be-at-least-32-chars-long";

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setUserPoolSecret(USER_SECRET);
        props.setAdminPoolSecret(ADMIN_SECRET);

        verifier = new JwtVerifier();
        // 反射注入 @Value 字段
        setField(verifier, "userPoolSecret", USER_SECRET);
        setField(verifier, "adminPoolSecret", ADMIN_SECRET);
        // 触发 @PostConstruct
        var m = JwtVerifier.class.getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(verifier);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = JwtVerifier.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private String mintToken(String secret, long userId, String role) throws Exception {
        // 用 del-common 的 JwtUtil 生成（同套 JJWT API）
        Class<?> utilCls = Class.forName("com.sakana.utils.JwtUtil");
        // 构造 JwtProperties 让 JwtUtil 用我们的密钥
        JwtProperties props = new JwtProperties();
        props.setUserPoolSecret(secret);
        // 反射绕过 @Service 的 JwtUtil 实例化
        var ctor = utilCls.getDeclaredConstructor(JwtProperties.class);
        ctor.setAccessible(true);
        Object util = ctor.newInstance(props);
        var m = utilCls.getDeclaredMethod("generateAccessToken", Long.class, String.class, String.class);
        return (String) m.invoke(util, userId, "test", role);
    }

    // ==================== user-pool 验证 ====================

    @Test
    void verify_userToken_returnsUserPool() throws Exception {
        String token = mintToken(USER_SECRET, 1001L, "USER");
        TokenInfo info = verifier.verify(token);

        assertNotNull(info, "user-pool-secret 签的 token 必须能验证");
        assertEquals(1001L, info.userId());
        assertEquals("USER", info.role());
        assertEquals(JwtVerifier.POOL_USER, info.source());
    }

    @Test
    void verify_adminToken_returnsAdminPool() throws Exception {
        String token = mintToken(ADMIN_SECRET, 9999L, "ADMIN");
        TokenInfo info = verifier.verify(token);

        assertNotNull(info, "admin-pool-secret 签的 token 必须能验证");
        assertEquals(9999L, info.userId());
        assertEquals("ADMIN", info.role());
        assertEquals(JwtVerifier.POOL_ADMIN, info.source());
    }

    @Test
    void verify_superAdminToken_returnsAdminPool() throws Exception {
        String token = mintToken(ADMIN_SECRET, 1L, "SUPER_ADMIN");
        TokenInfo info = verifier.verify(token);

        assertNotNull(info);
        assertEquals("SUPER_ADMIN", info.role());
    }

    // ==================== 无效 token ====================

    @Test
    void verify_invalidToken_returnsNull() {
        assertNull(verifier.verify("not.a.valid.token"));
        assertNull(verifier.verify("xxxxxx.yyyyyy.zzzzzz"));
    }

    @Test
    void verify_emptyOrNullToken_returnsNull() {
        assertNull(verifier.verify(null));
        assertNull(verifier.verify(""));
        assertNull(verifier.verify("   "));
    }

    @Test
    void verify_wrongSecretToken_returnsNull() throws Exception {
        // 用一个完全不同的密钥签的 token
        JwtProperties wrong = new JwtProperties();
        wrong.setUserPoolSecret("completely-different-secret-must-be-32-chars-or-more");
        Class<?> utilCls = Class.forName("com.sakana.utils.JwtUtil");
        var ctor = utilCls.getDeclaredConstructor(JwtProperties.class);
        ctor.setAccessible(true);
        Object util = ctor.newInstance(wrong);
        var m = utilCls.getDeclaredMethod("generateAccessToken", Long.class, String.class, String.class);
        String token = (String) m.invoke(util, 1L, "x", "USER");

        assertNull(verifier.verify(token), "陌生密钥签的 token 必须被拒");
    }

    // ==================== 一致性 ====================

    @Test
    void verify_priority_userFirst() throws Exception {
        // 即使 admin-pool-secret 配错了，user-token 仍能验证
        setField(verifier, "adminPoolSecret", "WRONG-ADMIN-SECRET-MUST-BE-32-CHARS-LONG!!");
        var m = JwtVerifier.class.getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(verifier);

        String userToken = mintToken(USER_SECRET, 100L, "USER");
        assertNotNull(verifier.verify(userToken), "admin 密钥错不应影响 user 验证");
    }
}
