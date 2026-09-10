package com.sakana.services;

import com.sakana.dao.entity.User;
import com.sakana.dto.request.admin.AdminUserListQuery;
import com.sakana.dto.request.admin.UserStatusReq;
import com.sakana.dto.request.RegisterReq;
import com.sakana.web.vo.AdminUserPageResp;

/**
 * 用户服务接口（C 端 + B 端）
 */
public interface UserService {

    // ==================== C 端 ====================

    /**
     * 用户注册
     */
    User register(RegisterReq req);

    /**
     * 根据用户名查询用户（供 Spring Security 认证使用）
     */
    User getByUsername(String username);

    /**
     * 根据 QQ openId 查询或创建用户
     *
     * @param openId   QQ open_id（唯一标识）
     * @param nickname QQ 昵称（可null）
     * @param avatar   QQ 头像 URL（可null）
     * @return 用户实体
     */
    User getOrCreateQqUser(String openId, String nickname, String avatar);

    // ==================== B 端（管理后台） ====================

    /**
     * 管理后台 - 用户分页（多条件：keyword/status）
     */
    AdminUserPageResp adminGetPage(AdminUserListQuery query);

    /**
     * 管理后台 - 用户详情
     */
    User adminGetDetail(Long userId);

    /**
     * 管理后台 - 冻结 / 解冻用户
     *
     * @param userId    目标用户 ID
     * @param req       新状态请求
     * @param adminId   操作管理员 ID
     * @param adminName 操作管理员用户名（仅日志）
     */
    void adminChangeStatus(Long userId, UserStatusReq req, Long adminId, String adminName);
}
