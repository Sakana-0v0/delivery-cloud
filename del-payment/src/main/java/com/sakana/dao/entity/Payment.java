package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_payment")
public class Payment extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付流水号（业务唯一）
     */
    private String payNo;

    /**
     * 支付渠道（alipay / wxpay）
     */
    private String channel;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付状态：0待支付 1成功 2失败 3关闭
     */
    private Integer status;

    /**
     * 支付成功时间
     */
    private LocalDateTime paidAt;

    /**
     * 支付宝交易号（回调后由支付宝返回）
     */
    private String tradeNo;

    /**
     * 原始回调数据
     */
    private String rawNotify;
}
