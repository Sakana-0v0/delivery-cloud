package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_free_order_activity")
public class FreeOrderActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT, value = "created_at")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE, value = "updated_at")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    private String name;
    private LocalDateTime startTime;
    private LocalDateTime grabTime;
    private LocalDateTime endTime;
    private Integer totalQuota;
    private BigDecimal maxFreeAmount;
    private String status;
    private Long createdBy;
}
