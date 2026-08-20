package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建订单请求
 */
@Schema(description = "创建订单请求")
public class OrderCreateReq {

    @NotEmpty(message = "订单商品不能为空")
    @Valid
    @Schema(description = "订单商品列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemReq> items = new ArrayList<>();

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50)
    @Schema(description = "收货人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiverName;

    @NotBlank(message = "收货人电话不能为空")
    @Size(max = 20)
    @Schema(description = "收货人电话", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    @Schema(description = "收货地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiverAddress;

    @Size(max = 500)
    @Schema(description = "订单备注")
    private String remark;

    public List<OrderItemReq> getItems() { return items; }
    public void setItems(List<OrderItemReq> items) { this.items = items; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
