package com.sakana.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathRoleUnitTest
 *
 * <p>网关鉴权核心：isPublic 决定是否放行，checkRole 决定角色匹配。
 * 这是整个微服务架构安全的"门神"，必须 100% 覆盖。
 */
class PathRoleRuleTest {

    // ==================== isPublic 测试 ====================

    @ParameterizedTest
    @CsvSource({
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/send-code",
            "/api/v1/auth/refresh",
            "/api/v1/admin/auth/login",
            "/api/v1/products",
            "/api/v1/products/1001",
            "/api/v1/products/hot",
            "/api/v1/categories",
            "/api/v1/categories/1",
            "/swagger-ui/index.html",
            "/swagger-ui",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/doc.html",
            "/actuator/health",
            "/actuator/metrics",
    })
    void isPublic_publicPaths_returnsTrue(String path) {
        assertTrue(PathRoleRule.isPublic(path), "应放行: " + path);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/user/orders",          // 私有
            "/api/v1/user/orders/1",
            "/api/v1/admin/products",        // admin 私有
            "/api/v1/admin/orders/1",
            "/api/v1/cart",                  // 购物车私有
            "/api/v1/cart/items",
            "/api/v1/messages",
            "/api/v1/admin/messages",       // 路径前缀不带 /api/v1/admin/ 后面
            "/api/v1/payments/notify",       // 支付回调
            "/api/v1/internal/orders/1",     // 内部
            "/favicon.ico",                  // 静态资源
            "/api/v1/auth/anything-else",    // 未知 auth 子路径
    })
    void isPublic_privatePaths_returnsFalse(String path) {
        assertFalse(PathRoleRule.isPublic(path), "应拦截: " + path);
    }

    // ==================== checkRole 测试 ====================

    // 1. /api/v1/admin/** 路径规则

    @Test
    void checkRole_adminPath_adminAllowed() {
        assertNull(PathRoleRule.checkRole("/api/v1/admin/products", "ADMIN"));
        assertNull(PathRoleRule.checkRole("/api/v1/admin/users/1", "ADMIN"));
    }

    @Test
    void checkRole_adminPath_superAdminAllowed() {
        assertNull(PathRoleRule.checkRole("/api/v1/admin/stats/overview", "SUPER_ADMIN"));
    }

    @Test
    void checkRole_adminPath_userDenied() {
        String result = PathRoleRule.checkRole("/api/v1/admin/products", "USER");
        assertNotNull(result);
        assertEquals("需要管理员权限", result);
    }

    @Test
    void checkRole_adminPath_nullRoleDenied() {
        assertNotNull(PathRoleRule.checkRole("/api/v1/admin/orders", null));
    }

    // 2. /api/v1/user/** 路径规则

    @Test
    void checkRole_userPath_userAllowed() {
        assertNull(PathRoleRule.checkRole("/api/v1/user/orders", "USER"));
        assertNull(PathRoleRule.checkRole("/api/v1/user/addresses", "USER"));
    }

    @Test
    void checkRole_userPath_adminDenied() {
        String result = PathRoleRule.checkRole("/api/v1/user/orders", "ADMIN");
        assertNotNull(result);
        assertEquals("需要普通用户权限", result);
    }

    // 3. /api/v1/cart/** 路径规则

    @Test
    void checkRole_cartPath_userAllowed() {
        assertNull(PathRoleRule.checkRole("/api/v1/cart", "USER"));
        assertNull(PathRoleRule.checkRole("/api/v1/cart/items", "USER"));
    }

    @Test
    void checkRole_cartPath_adminDenied() {
        // 购物车是 C 端私有，admin 也不能访问
        String result = PathRoleRule.checkRole("/api/v1/cart", "ADMIN");
        assertNotNull(result);
    }

    // 4. 其他已认证路径

    @Test
    void checkRole_otherPath_anyRoleAllowed() {
        assertNull(PathRoleRule.checkRole("/api/v1/payments/notify", "USER"));
        assertNull(PathRoleRule.checkRole("/api/v1/messages", "ADMIN"));
        assertNull(PathRoleRule.checkRole("/api/v1/internal/orders/1", "USER"));
    }

    // 5. 边界场景

    @Test
    void checkRole_pathWithQueryString_handledByStartsWith() {
        // 即使带 query string 也走路径前缀匹配
        // 注：实际是 strip query string 后判断
        assertNull(PathRoleRule.checkRole("/api/v1/admin/orders?status=2", "ADMIN"));
    }
}
