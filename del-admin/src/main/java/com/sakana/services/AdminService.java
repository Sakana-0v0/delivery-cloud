package com.sakana.services;

import com.sakana.request.AdminLoginReq;
import com.sakana.web.vo.LoginResp;

/**
 * 管理员服务接口
 */
public interface AdminService {

    /**
     * 管理员登录
     */
    LoginResp login(AdminLoginReq req);

    /**
     * 获取当前管理员信息
     */
    LoginResp.UserInfo me(Long adminId);

    /**
     * 管理员登出（写黑名单）
     */
    void logout(String jti, long expireSeconds);
}
