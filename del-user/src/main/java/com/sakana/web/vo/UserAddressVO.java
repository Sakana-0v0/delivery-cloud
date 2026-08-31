package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收货地址视图
 */
@Data
public class UserAddressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 拼接后的完整地址 */
    private String fullAddress;
    private Integer isDefault;
    private LocalDateTime createTime;
}
