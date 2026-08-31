package com.sakana.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * del-user 认证登录主体（扩展 del-common 的 LoginUser，增加 password / nickname 字段）
 *
 * <p>仅用于 C 端登录场景（Spring Security form-login）。
 * 双密钥 JWT 认证场景下仍使用 del-common 的 LoginUser。
 */
public class AuthLoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String password;

    public AuthLoginUser(Long userId, String username, String nickname, String role, String password) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.password = password;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
