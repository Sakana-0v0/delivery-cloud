package com.sakana.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 认证主体（统一版：C 端与 B 端共用）
 *
 * <p>无论是 C 端 user-pool 还是 B 端 admin-pool 解析出的 token，
 * 最终都包装为 LoginUser 放入 SecurityContext。
 * <p>
 * 角色判定：调用方按需读取 getRole() / 检查 authorities。
 */
public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String role;

    public LoginUser(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public LoginUser() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
