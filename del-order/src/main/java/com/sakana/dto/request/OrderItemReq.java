package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 创建订单中的单项
 */
@Schema(description = "订单商品项")
public class OrderItemReq {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @NotNull(message = "商品名称不能为空")
    @Schema(description = "商品名称（快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;

    @Schema(description = "商品封面图URL（快照）")
    private String productCover;

    @NotNull(message = "商品单价不能为空")
    @Schema(description = "商品单价（快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal productPrice;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCover() { return productCover; }
    public void setProductCover(String productCover) { this.productCover = productCover; }

    public BigDecimal getProductPrice() { return productPrice; }
    public void setProductPrice(BigDecimal productPrice) { this.productPrice = productPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
