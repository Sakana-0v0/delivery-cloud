package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * 库存调整请求
 * <p>
 * 二选一：
 * <ul>
 *   <li>{@code stock}：设置库存到指定值（绝对值）</li>
 *   <li>{@code delta}：相对于当前库存的增减量（可负）</li>
 * </ul>
 */
@Data
@Schema(description = "库存调整请求：stock 设为指定值，delta 增减")
public class StockAdjustReq {

    @Schema(description = "设置库存到指定值（与 delta 二选一）", example = "100")
    private Integer stock;

    @Schema(description = "增减量（可负，与 stock 二选一）", example = "10")
    private Integer delta;

    /**
     * 自校验：stock 与 delta 必须有且仅有一个非空
     */
    @AssertTrue(message = "stock 与 delta 必须二选一")
    @Schema(hidden = true)
    public boolean isValid() {
        boolean stockPresent = stock != null;
        boolean deltaPresent = delta != null;
        return stockPresent ^ deltaPresent;
    }
}
