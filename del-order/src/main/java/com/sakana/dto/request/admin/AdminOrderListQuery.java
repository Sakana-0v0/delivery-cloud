package com.sakana.dto.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 订单查询请求
 */
@Data
@Schema(description = "管理后台订单列表查询参数")
public class AdminOrderListQuery {

    @Schema(description = "订单状态：1待支付 2已支付 3配送中 4已完成 5已取消",
            allowableValues = {"1","2","3","4","5"})
    private Integer status;

    @Schema(description = "用户ID（精确过滤）")
    private Long userId;

    @Schema(description = "订单号（模糊）")
    private String orderNo;

    @Schema(description = "起始创建时间（含）")
    private LocalDateTime startTime;

    @Schema(description = "截止创建时间（含）")
    private LocalDateTime endTime;

    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", defaultValue = "10")
    private Integer size = 10;
}
