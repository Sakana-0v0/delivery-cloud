package com.sakana.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security 工具类（统一版）
 *
 * <p>C 端 + B 端共用：principal 统一为 {@link LoginUser}。
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    public static LoginUser getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser ? (LoginUser) principal : null;
    }

    public static Long getCurrentUserId() {
        LoginUser u = getLoginUser();
        return u != null ? u.getUserId() : null;
    }

    public static String getCurrentUsername() {
        LoginUser u = getLoginUser();
        return u != null ? u.getUsername() : null;
    }

    public static String getCurrentRole() {
        LoginUser u = getLoginUser();
        return u != null ? u.getRole() : null;
    }

    public static boolean isAdmin() {
        String r = getCurrentRole();
        return "ADMIN".equals(r) || "SUPER_ADMIN".equals(r);
    }

    public static boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(getCurrentRole());
    }
}
