package com.sakana.admin.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.admin.dao.entity.Admin;
import com.sakana.admin.dao.mapper.AdminMapper;
import com.sakana.admin.dto.request.AdminLoginReq;
import com.sakana.admin.security.AdminTokenBlacklist;
import com.sakana.admin.services.AdminService;
import com.sakana.admin.services.AdminErrorCode;
import com.sakana.admin.web.vo.AdminLoginResp;
import com.sakana.exceptions.BizException;
import com.sakana.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AdminTokenBlacklist tokenBlacklist;

    @Override
    public AdminLoginResp login(AdminLoginReq req) {
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>()
                        .eq(Admin::getUsername, req.getUsername())
                        .eq(Admin::getIsDeleted, 0)
        );
        if (admin == null) {
            log.warn("[管理员登录失败] 用户不存在: {}", req.getUsername());
            throw new BizException(AdminErrorCode.LOGIN_FAIL);
        }

        if (admin.getStatus() != null && admin.getStatus() == 1) {
            log.warn("[管理员登录失败] 账号已禁用: {}", req.getUsername());
            throw new BizException(AdminErrorCode.ADMIN_FROZEN);
        }

        if (!passwordEncoder.matches(req.getPassword(), admin.getPassword())) {
            log.warn("[管理员登录失败] 密码错误: {}", req.getUsername());
            throw new BizException(AdminErrorCode.LOGIN_FAIL);
        }

        admin.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(admin);

        String role = admin.getRole();
        String accessToken = jwtUtil.generateAccessToken(admin.getId(), admin.getUsername(), role);
        String refreshToken = jwtUtil.generateRefreshToken(admin.getId(), admin.getUsername(), role);

        AdminLoginResp resp = new AdminLoginResp();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresIn(jwtUtil.getExpirationSeconds(accessToken));

        AdminLoginResp.UserInfo userInfo = new AdminLoginResp.UserInfo();
        userInfo.setId(admin.getId());
        userInfo.setUsername(admin.getUsername());
        userInfo.setNickname(admin.getNickname());
        userInfo.setRole(role);
        resp.setUserInfo(userInfo);

        log.info("[管理员登录成功] adminId={}, username={}, role={}",
                admin.getId(), admin.getUsername(), role);

        tokenBlacklist.registerUserJti(admin.getId(), jwtUtil.getJti(accessToken));
        return resp;
    }

    @Override
    public AdminLoginResp.UserInfo me(Long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null || admin.getIsDeleted() == 1) {
            throw new BizException(AdminErrorCode.ADMIN_NOT_FOUND);
        }
        AdminLoginResp.UserInfo info = new AdminLoginResp.UserInfo();
        info.setId(admin.getId());
        info.setUsername(admin.getUsername());
        info.setNickname(admin.getNickname());
        info.setRole(admin.getRole());
        return info;
    }

    @Override
    public void logout(String jti, long expireSeconds) {
        tokenBlacklist.add(jti, expireSeconds);
        log.info("[管理员登出] jti={} 已加入黑名单", jti);
    }
}
