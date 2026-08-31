package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单快照（跨服务契约，下沉到 del-common）。
 * <p>
 * 由 del-order 通过 Feign 返回给 del-payment，用于创建支付前的鉴权 + 业务校验 + 用户快照。
 * <p>
 * 字段选择原则：del-payment 在 createPayment / handleNotify 全过程只用到这些字段；
 * 用户名 / 邮箱随快照进入支付域，避免回调时再查用户库（解耦关键设计）。
 */
@Data
public class OrderSnapshotVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    private String email;
    private BigDecimal payAmount;
    /** 1 待支付 2 已支付 3 配送中 4 已完成 5 已取消 6 退款 */
    private Integer status;
    /** del-order 提供的"是否可发起支付"判断结果 */
    private Boolean canPay;
}
