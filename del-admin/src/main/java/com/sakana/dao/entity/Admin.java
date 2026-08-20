package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理员实体
 * <p>
 * 与 C 端用户 t_user 表完全独立，由 del-admin 服务独享。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin")
public class Admin extends BaseEntity {

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色（SUPER_ADMIN / ADMIN）
     */
    private String role;

    /**
     * 状态（0=启用，1=禁用）
     */
    private Integer status;

    /**
     * 最近登录时间
     */
    private LocalDateTime lastLoginTime;
}
