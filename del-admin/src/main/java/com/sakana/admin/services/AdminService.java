package com.sakana.admin.services;

import com.sakana.admin.dto.request.AdminLoginReq;
import com.sakana.admin.web.vo.AdminLoginResp;

/**
 * 管理员服务接口
 */
public interface AdminService {

    /**
     * 管理员登录
     */
    AdminLoginResp login(AdminLoginReq req);

    /**
     * 获取当前管理员信息
     */
    AdminLoginResp.UserInfo me(Long adminId);

    /**
     * 管理员登出（写黑名单）
     */
    void logout(String jti, long expireSeconds);
}
