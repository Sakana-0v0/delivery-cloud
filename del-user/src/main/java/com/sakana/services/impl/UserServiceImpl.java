package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.event.UserRegisteredEvent;
import com.sakana.exceptions.BizException;
import com.sakana.request.RegisterReq;
import com.sakana.services.UserService;
import com.sakana.services.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterReq req) {
        // 1. 校验验证码
        verifyCodeService.verifyCode(req.getEmail(), req.getCode());

        // 2. 校验用户名唯一
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, req.getUsername())
                        .eq(User::getIsDeleted, 0)
        );
        if (count > 0) {
            throw new BizException(UserErrorCode.USERNAME_EXISTS);
        }

        // 3. 校验手机号唯一（如果有）
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            long phoneCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, req.getPhone())
                            .eq(User::getIsDeleted, 0)
            );
            if (phoneCount > 0) {
                throw new BizException(UserErrorCode.PHONE_EXISTS);
            }
        }

        // 4. 校验邮箱唯一
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, req.getEmail())
                            .eq(User::getIsDeleted, 0)
            );
            if (emailCount > 0) {
                throw new BizException(UserErrorCode.EMAIL_EXISTS);
            }
        }

        // 5. 创建用户
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        log.info("[用户注册] 成功: userId={}, username={}", user.getId(), user.getUsername());

        // 6. 发布注册成功事件
        eventPublisher.publishEvent(new UserRegisteredEvent(
                this, user.getId(), user.getUsername(), user.getEmail()));

        return user;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getIsDeleted, 0)
        );
    }
}
