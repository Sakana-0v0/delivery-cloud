package com.sakana.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 管理员认证主体
 * <p>
 * 与 del-user 的 LoginUser 完全独立，避免类型混淆。
 */
@Data
public class AdminLoginUser implements UserDetails {

    private Long adminId;
    private String username;
    private String nickname;
    private String role;
    private String password;

    public AdminLoginUser(Long adminId, String username, String nickname, String role) {
        this.adminId = adminId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
    }

    public AdminLoginUser(Long adminId, String username, String nickname, String role, String password) {
        this.adminId = adminId;
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
