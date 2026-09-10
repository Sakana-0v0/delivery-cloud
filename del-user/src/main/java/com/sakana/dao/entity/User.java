package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码（BCrypt 加密，QQ 用户可空）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 状态（0=正常，1=冻结）
     */
    private Integer status;

    /**
     * 最近登录时间
     */
    private LocalDateTime lastLoginTime;

    // ========== QQ 第三方登录字段 ==========

    /**
     * QQ open_id（唯一，QQ 用户标识）
     */
    @TableField("open_id")
    private String openId;

    /**
     * QQ 昵称缓存
     */
    @TableField("qq_nickname")
    private String qqNickname;

    /**
     * QQ 头像 URL（100x100）
     */
    @TableField("qq_avatar")
    private String qqAvatar;
}