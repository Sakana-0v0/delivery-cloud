package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售趋势 - 单点
 */
@Data
public class SalesTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日期键：day=yyyy-MM-dd，month=yyyy-MM */
    private String dateKey;
    /** 订单数 */
    private Long orderCount;
    /** 销售额（已支付+之后） */
    private BigDecimal salesAmount;
}
