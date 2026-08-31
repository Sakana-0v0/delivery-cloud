package com.sakana.dto.request.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户冻结/解冻请求
 */
@Data
public class UserStatusReq {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
