package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品创建/修改请求
 */
@Data
@Schema(description = "商品创建/修改请求")
public class ProductReq {

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称长度不能超过 100")
    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 255, message = "封面URL长度不能超过 255")
    @Schema(description = "封面图片URL/路径")
    private String cover;

    @Schema(description = "商品描述")
    private String description;

    @NotNull(message = "原价不能为空")
    @DecimalMin(value = "0.00", message = "原价不能为负")
    @Schema(description = "原价", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal normPrice;

    @NotNull(message = "现价不能为空")
    @DecimalMin(value = "0.00", message = "现价不能为负")
    @Schema(description = "现价（实际售价）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal realPrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    @Schema(description = "初始库存", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stock;

    @Schema(description = "状态：0上架 1下架", defaultValue = "0", allowableValues = {"0", "1"})
    private Integer status = 0;
}