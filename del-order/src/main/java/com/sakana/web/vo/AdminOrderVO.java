package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 订单视图（带用户信息）
 */
@Data
public class AdminOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    private String nickname;

    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private String statusDesc;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;

    private LocalDateTime deliveryTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private LocalDateTime createTime;

    private List<OrderItemVO> items;
}
