package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

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

    /** 是否为免单（0元支付） */
    private Boolean freeOrder;

    /** 免单码 */
    private String freeOrderCode;

    /** 实付金额（免单时为0） */
    private BigDecimal paidAmount;

    /** 支付状态：SUCCESS / PENDING / FAILED */
    private String status;
}