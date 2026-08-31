package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 管理后台 - 订单分页响应
 */
@Data
public class AdminOrderPageResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Long page;
    private Long size;
    private List<AdminOrderVO> records;
}
