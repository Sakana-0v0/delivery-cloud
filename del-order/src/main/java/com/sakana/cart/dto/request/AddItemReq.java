package com.sakana.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 加入购物车请求
 */
@Data
@Schema(description = "加入购物车请求")
public class AddItemReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1001")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于等于1")
    @Schema(description = "商品数量", example = "1")
    private Integer quantity;
}
