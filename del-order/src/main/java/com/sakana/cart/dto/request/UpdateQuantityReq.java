package com.sakana.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改购物车数量请求
 */
@Data
@Schema(description = "修改购物车商品数量请求")
public class UpdateQuantityReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量必须大于等于0，0 表示删除该商品")
    @Schema(description = "商品数量，0 表示删除该商品", example = "2")
    private Integer quantity;
}
