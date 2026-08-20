package com.sakana.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 管理员 Security 工具类
 */
@Slf4j
public class AdminSecurityUtil {

    /**
     * 获取当前登录管理员
     */
    public static AdminLoginUser getLoginAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminLoginUser) {
            return (AdminLoginUser) principal;
        }
        return null;
    }

    /**
     * 获取当前登录管理员 ID
     */
    public static Long getCurrentAdminId() {
        AdminLoginUser admin = getLoginAdmin();
        return admin != null ? admin.getAdminId() : null;
    }

    /**
     * 获取当前登录管理员用户名
     */
    public static String getCurrentUsername() {
        AdminLoginUser admin = getLoginAdmin();
        return admin != null ? admin.getUsername() : null;
    }

    /**
     * 获取当前登录管理员角色
     */
    public static String getCurrentRole() {
        AdminLoginUser admin = getLoginAdmin();
        return admin != null ? admin.getRole() : null;
    }

    /**
     * 判断当前管理员是否拥有某个角色
     */
    public static boolean hasRole(String role) {
        String currentRole = getCurrentRole();
        return currentRole != null && currentRole.equals(role);
    }

    /**
     * 是否超管
     */
    public static boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    /**
     * 是否管理员（含超管）
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN") || isSuperAdmin();
    }
}
