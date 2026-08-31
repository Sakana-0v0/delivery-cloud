package com.sakana.dto.request.admin;

import lombok.Data;

/**
 * 管理后台 - 用户查询请求
 */
@Data
public class AdminUserListQuery {

    private String keyword;
    private Integer status;
    private Integer page = 1;
    private Integer size = 10;
}
