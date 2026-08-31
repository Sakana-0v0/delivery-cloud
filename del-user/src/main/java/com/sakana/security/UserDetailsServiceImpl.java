package com.sakana.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * C 端用户登录认证
 * <p>
 * 从 t_user 表加载用户信息，返回自定义 AuthLoginUser（含 userId / password）。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getIsDeleted, 0)
        );

        if (user == null) {
            log.warn("[登录] 用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在");
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("[登录] 用户已被冻结: {}", username);
            throw new UsernameNotFoundException("用户已被冻结");
        }

        log.debug("[登录] 用户加载成功: userId={}, username={}", user.getId(), user.getUsername());

        return new AuthLoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername(),
                "USER",
                user.getPassword()
        );
    }
}
