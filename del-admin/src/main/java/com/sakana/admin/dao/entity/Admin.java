package com.sakana.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sakana.dao.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理员实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin")
public class Admin extends BaseEntity {

    private String username;

    private String password;

    private String nickname;

    private String role;

    private Integer status;

    private LocalDateTime lastLoginTime;
}
