package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_free_order_coupon")
public class FreeOrderCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT, value = "created_at")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE, value = "updated_at")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    private Long activityId;
    private String code;
    private BigDecimal maxAmount;
    private String status;
    private Long grabbedUserId;
    private LocalDateTime grabbedTime;
    private Long usedOrderId;
    private LocalDateTime usedTime;
}
