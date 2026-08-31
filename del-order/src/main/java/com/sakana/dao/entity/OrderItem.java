package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 订单项
 *
 * <p>字段映射：
 * <ul>
 *   <li>product_price  → DB price</li>
 *   <li>subtotalAmount → DB subtotal</li>
 * </ul>
 */
@TableName("t_order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private Long productId;
    private String productName;
    private String productCover;

    @TableField("price")
    private BigDecimal productPrice;

    private Integer quantity;

    @TableField("subtotal")
    private BigDecimal subtotalAmount;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

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

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }
}
