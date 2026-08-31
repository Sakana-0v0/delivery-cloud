package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付创建响应视图
 */
@Data
public class PaymentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String payUrl;
    private String payForm;
}