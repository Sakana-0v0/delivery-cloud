package com.sakana.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

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
}
