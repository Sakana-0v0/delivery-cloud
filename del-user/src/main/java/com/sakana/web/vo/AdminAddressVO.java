package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理后台 - 收货地址记录
 */
@Data
public class AdminAddressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private String fullAddress;
    private Integer isDefault;
    private LocalDateTime createTime;
}
