package com.sakana.dto.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 修改订单状态请求
 */
@Data
@Schema(description = "管理后台修改订单状态请求")
public class AdminOrderStatusReq {

    @NotNull(message = "目标状态不能为空")
    @Schema(description = "目标状态：1待支付 2已支付 3配送中 4已完成 5已取消",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"1","2","3","4","5"})
    private Integer status;

    @Schema(description = "修改原因（仅记录到日志，便于审计）")
    private String remark;
}
