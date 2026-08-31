package com.sakana.web.vo;

import com.sakana.dao.entity.Payment;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对内（del-order / del-comment）暴露的支付信息视图。
 * 通过 Feign {@code PaymentQueryClient.getByOrderNo} 返回。
 */
@Data
public class PaymentInternalVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String payNo;
    private String channel;
    private BigDecimal amount;
    /** 0 待支付 1 成功 2 失败 3 关闭 */
    private Integer status;
    private LocalDateTime paidAt;
    private String tradeNo;

    public static PaymentInternalVO from(Payment p) {
        if (p == null) return null;
        PaymentInternalVO vo = new PaymentInternalVO();
        vo.setOrderNo(p.getOrderNo());
        vo.setPayNo(p.getPayNo());
        vo.setChannel(p.getChannel());
        vo.setAmount(p.getAmount());
        vo.setStatus(p.getStatus());
        vo.setPaidAt(p.getPaidAt());
        vo.setTradeNo(p.getTradeNo());
        return vo;
    }
}
