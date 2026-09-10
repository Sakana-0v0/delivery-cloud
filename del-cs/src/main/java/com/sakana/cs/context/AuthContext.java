package com.sakana.cs.context;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * 当前请求的认证上下文（ThreadLocal）
 * 用于在同一次请求中跨组件传递用户 token / userId
 *
 * <p>Bug 修复 #BUG-011：原实现从 body 读 userId，前端不传会导致所有用户共享一个会话
 * <p>现改为从 JWT token 解析 subject（userId）
 */
public class AuthContext {

    private static final ThreadLocal<String> AUTH_TOKEN = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void setToken(String token) {
        AUTH_TOKEN.set(token);
        // 同步解析并设置 userId（不抛异常，解析失败就留空）
        USER_ID.set(parseUserIdSafe(token));
    }

    public static String getToken() {
        return AUTH_TOKEN.get();
    }

    public static String getUserId() {
        String uid = USER_ID.get();
        if (uid == null || uid.isBlank()) {
            return "anonymous";
        }
        return uid;
    }

    public static void clear() {
        AUTH_TOKEN.remove();
        USER_ID.remove();
    }

    /**
     * 提取 Bearer token，去掉 "Bearer " 前缀
     */
    public static String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ") || authHeader.startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader.trim();
    }

    /**
     * 解析 JWT token 的 subject 作为 userId，失败返回 null
     * 不需要密钥校验（让 Gateway / Security 已经验证过；这里仅做 base64 解码）
     */
    private static String parseUserIdSafe(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            // 不验签，只读 payload（gateway 已经验过了）
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            // 简单提取 "sub":"xxx"
            int idx = json.indexOf("\"sub\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int comma = json.indexOf(',', colon);
            int brace = json.indexOf('}', colon);
            int end = (comma > 0 && (brace < 0 || comma < brace)) ? comma : brace;
            String sub = json.substring(colon + 1, end).trim();
            if (sub.startsWith("\"")) sub = sub.substring(1);
            if (sub.endsWith("\"")) sub = sub.substring(0, sub.length() - 1);
            return sub;
        } catch (Exception e) {
            return null;
        }
    }
}
