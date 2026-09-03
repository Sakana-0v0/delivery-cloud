package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户收货地址视图（内部调用，简化版）
 */
@Data
public class UserAddressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 拼接后的完整地址 */
    private String fullAddress;
    private Integer isDefault;
}