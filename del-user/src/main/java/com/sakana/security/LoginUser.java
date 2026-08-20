package com.sakana.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户信息
 * <p>
 * 实现 UserDetails，作为 Spring Security 认证主体的载体。
 */
@Data
public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String password;

    // 4 参数构造方法（不带 password，供 JwtAuthenticationFilter 使用）
    public LoginUser(Long userId, String username, String nickname, String role) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
    }

    // 5 参数构造方法（含 password，供 UserDetailsServiceImpl 使用）
    public LoginUser(Long userId, String username, String nickname, String role, String password) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.password = password;
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
