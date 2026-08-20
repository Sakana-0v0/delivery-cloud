package com.sakana.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security 工具类
 * <p>
 * 提供获取当前登录用户信息的静态方法。
 */
@Slf4j
public class SecurityUtil {

    /**
     * 获取当前登录用户信息
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }

    /**
     * 获取当前登录用户 ID
     */
    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUsername() : null;
    }

    /**
     * 获取当前登录用户角色
     */
    public static String getCurrentRole() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getRole() : null;
    }

    /**
     * 判断当前用户是否拥有某个角色
     */
    public static boolean hasRole(String role) {
        String currentRole = getCurrentRole();
        return currentRole != null && currentRole.equals(role);
    }

    /**
     * 判断是否是超管
     */
    public static boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    /**
     * 判断是否是管理员
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN") || isSuperAdmin();
    }
}
