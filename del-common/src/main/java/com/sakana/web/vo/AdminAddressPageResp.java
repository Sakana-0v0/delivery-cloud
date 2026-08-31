package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 管理后台 - 收货地址分页响应
 */
@Data
public class AdminAddressPageResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Integer page;
    private Integer size;
    private List<?> records;
}
