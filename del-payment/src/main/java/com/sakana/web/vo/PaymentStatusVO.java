package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态响应视图（对外 C 端 + 后台）。
 */
@Data
public class PaymentStatusVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String payNo;
    private Integer status;
    private LocalDateTime paidAt;
    private BigDecimal amount;
    /** 支付宝交易号 */
    private String tradeNo;
    /** 买家支付宝用户ID */
    private String buyerUserId;
    /** 买家支付宝账号（脱敏） */
    private String buyerLogonId;
}
