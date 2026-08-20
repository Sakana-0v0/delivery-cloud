package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架请求
 */
@Data
@Schema(description = "商品上下架请求")
public class ProductStatusReq {

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：0上架 1下架",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"0", "1"})
    private Integer status;
}
