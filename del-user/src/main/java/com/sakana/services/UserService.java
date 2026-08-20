package com.sakana.services;

import com.sakana.dao.entity.User;
import com.sakana.request.RegisterReq;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param req 注册请求（含验证码校验）
     * @return 注册成功的用户（password 字段已置空）
     */
    User register(RegisterReq req);

    /**
     * 根据用户名查询用户（供 Spring Security 认证使用）
     */
    User getByUsername(String username);
}
