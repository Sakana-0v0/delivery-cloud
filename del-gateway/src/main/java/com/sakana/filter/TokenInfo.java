package com.sakana.filter;

/**
 * Token 解析后的信息
 */
public record TokenInfo(Long userId, String username, String role, String source) {
}
