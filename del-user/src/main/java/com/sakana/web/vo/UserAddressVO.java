package com.sakana.web.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private String fullAddress;
    private Integer isDefault;
    private LocalDateTime createTime;
}
