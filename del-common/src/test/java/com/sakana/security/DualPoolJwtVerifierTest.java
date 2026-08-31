package com.sakana.security;

import com.sakana.configs.JwtProperties;
import com.sakana.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DualPoolJwtVerifier 单元测试
 *
 * <p>验证双密钥机制：user-pool / admin-pool 都能正确解析。
 */
class DualPoolJwtVerifierTest {

    private DualPoolJwtVerifier verifier;
    private JwtUtil userJwtUtil;
    private JwtProperties userProps;

    @BeforeEach
    void setUp() {
        userProps = new JwtProperties();
        userProps.setUserPoolSecret("test-user-pool-secret-key-1234567890");
        userProps.setAdminPoolSecret("test-admin-pool-secret-key-1234567890");
        userProps.setAccessExpireSeconds(3600);

        userJwtUtil = new JwtUtil(userProps);

        verifier = new DualPoolJwtVerifier(userProps);
        verifier.init();
    }

    @Test
    void verify_userPoolToken_returnsUserPool() {
        String token = userJwtUtil.generateAccessToken(1001L, "alice", "USER");
        DualPoolJwtVerifier.VerifiedToken result = verifier.verify(token);
        assertNotNull(result);
        assertEquals(1001L, result.userId());
        assertEquals("alice", result.username());
        assertEquals("USER", result.role());
        assertEquals(DualPoolJwtVerifier.POOL_USER, result.pool());
        assertFalse(result.isAdmin());
    }

    @Test
    void verify_invalidToken_returnsNull() {
        DualPoolJwtVerifier.VerifiedToken result = verifier.verify("not.a.real.token");
        assertNull(result);
    }

    @Test
    void verify_emptyToken_returnsNull() {
        assertNull(verifier.verify(null));
        assertNull(verifier.verify(""));
        assertNull(verifier.verify("   "));
    }

    @Test
    void isAdminPoolEnabled_whenConfigured_returnsTrue() {
        assertTrue(verifier.isAdminPoolEnabled());
    }

    @Test
    void isAdminPoolEnabled_whenNotConfigured_returnsFalse() {
        JwtProperties props = new JwtProperties();
        DualPoolJwtVerifier v = new DualPoolJwtVerifier(props);
        v.init();
        assertFalse(v.isAdminPoolEnabled());
    }

    @Test
    void jwtUtil_backwardCompatibility_worksWithLegacySecretField() {
        JwtProperties legacyProps = new JwtProperties();
        legacyProps.setSecret("legacy-secret-1234567890");
        JwtUtil legacyUtil = new JwtUtil(legacyProps);
        String token = legacyUtil.generateAccessToken(2002L, "bob", "USER");
        DualPoolJwtVerifier v = new DualPoolJwtVerifier(legacyProps);
        v.init();
        DualPoolJwtVerifier.VerifiedToken result = v.verify(token);
        assertNotNull(result, "向后兼容：用 secret 字段生成的 token 应仍能验证");
        assertEquals(2002L, result.userId());
    }
}
