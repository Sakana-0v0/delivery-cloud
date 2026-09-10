package com.sakana.filter;

/**
 * 路径访问规则
 *
 * <p>采用"角色前缀 + 业务前缀"模式：
 * <ul>
 *   <li>/api/v1/admin/**    → 需 ADMIN/SUPER_ADMIN</li>
 *   <li>/api/v1/payments/return, /notify → 支付宝回调公开
 *   <li>/api/v1/user/**     → 需 USER</li>
 *   <li>/api/v1/cart/**     → 需 USER（购物车是 C 端私有接口）</li>
 *   <li>/internal/**        → 内部服务调用白名单（需 X-Internal-Service-Token）</li>
 *   <li>其他已认证路径      → 任意合法角色放行</li>
 * </ul>
 *
 * <p>内部 API (/internal/**) 安全机制：
 * <ul>
 *   <li>这些是微服务之间内部调用的端点</li>
 *   <li>通过 Feign 客户端调用，携带 X-Internal-Service-Token 头</li>
 *   <li>网关验证 Token 有效性，拒绝未授权的内部调用</li>
 *   <li>每个微服务的 SecurityConfig 已配置要求 ROLE_INTERNAL_SERVICE</li>
 * </ul>
 */
public final class PathRoleRule {

    /** 内部服务调用专用 Header */
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    /** 内部服务调用专用 Token（需与各微服务 Feign 客户端配置保持一致） */
    public static final String INTERNAL_SERVICE_TOKEN = "internal-service-secret-key-2024";

    /** 内部服务角色（各微服务 SecurityConfig 要求的角色） */
    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";

    private PathRoleRule() {}

    /**
     * 判断路径是否无需认证即可访问
     *
     * <p>注意：/internal/** 在此返回 false，实际验证在 {@link #checkInternalServiceToken} 中进行
     */
    public static boolean isPublic(String path) {
        // ==================== C 端公开接口 ====================
        // 登录/注册/验证码/刷新 token
        if (path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/send-code")
                || path.equals("/api/v1/auth/refresh")) {
            return true;
        }

        // QQ 第三方登录回调（无需 JWT，心月互联授权后回调）
        if (path.startsWith("/api/v1/auth/qq/")) {
            return true;
        }

        // ==================== B 端公开接口 ====================
        // 管理员登录
        if (path.equals("/api/v1/admin/auth/login")) {
            return true;
        }

        // ==================== 商品浏览公开 ====================
        // 商品/分类公开（C 端游客可访问）
        if (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories")) {
            return true;
        }

        // ==================== 支付宝回调公开（PAY-003）====================
        // /return 是浏览器302跳转（Alipay 沙箱），不带 JWT，必须公开
        // /notify 是 Alipay 服务器→服务器异步回调，有 rsaCheckV1 验签保护
        if (path.equals("/api/v1/payments/return") || path.equals("/api/v1/payments/notify")) {
            return true;
        }

        // ==================== 智能客服公开（#AI-CS-001-MVP）====================
        // /api/v1/cs/** 无需认证（JWT 由 del-cs 自行决定）
        if (path.startsWith("/api/v1/cs/")) {
            return true;
        }

        // ==================== 内部服务调用白名单 ====================
        // /internal/** 路径：微服务之间内部调用
        // 注意：不在此放行，而是在 checkInternalServiceToken 中验证 Token
        // 这样即使路径匹配 /internal/**，没有正确 Token 也无法访问
        if (path.startsWith("/internal/")) {
            return false;  // 返回 false，由 checkInternalServiceToken 处理
        }

        // ==================== 监控与文档 ====================
        // API 文档与监控
        if (path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/doc.html")
                || path.startsWith("/actuator/")) {
            return true;
        }

        return false;
    }

    /**
     * 判断已认证的角色是否能访问该路径
     *
     * @return null 表示允许；非 null 表示 403 原因
     */
    public static String checkRole(String path, String role) {
        // ==================== 管理后台路径 ====================
        // /api/v1/admin/** 需 ADMIN / SUPER_ADMIN
        if (path.startsWith("/api/v1/admin/")) {
            if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                return null;
            }
            return "需要管理员权限";
        }

        // ==================== C 端用户路径 ====================
        // /api/v1/user/** 需普通用户
        if (path.startsWith("/api/v1/user/")) {
            if ("USER".equals(role)) {
                return null;
            }
            return "需要普通用户权限";
        }

        // ==================== 购物车路径 ====================
        // /api/v1/cart/** 需普通用户（购物车为 C 端私有）
        if (path.startsWith("/api/v1/cart/") || path.equals("/api/v1/cart")) {
            if ("USER".equals(role)) {
                return null;
            }
            return "需要普通用户权限";
        }

        // ==================== 内部 API 路径 ====================
        // /internal/** 由 checkInternalServiceToken 处理，此处不做角色检查
        if (path.startsWith("/internal/")) {
            return null;  // 先放行，checkInternalServiceToken 会验证
        }

        // 其他已认证路径：任意合法角色均放行
        return null;
    }

    /**
     * 验证内部服务调用 Token
     *
     * <p>用于 /internal/** 路径的额外验证，防止未授权的内部调用
     *
     * @param token 请求中的 X-Internal-Service-Token 头值
     * @return true Token 有效；false Token 无效
     */
    public static boolean checkInternalServiceToken(String token) {
        return INTERNAL_SERVICE_TOKEN.equals(token);
    }
}