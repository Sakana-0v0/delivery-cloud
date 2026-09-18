package com.sakana.review.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量投票统计入参项：单个订单项 (orderId, productId)
 */
@Data
public class BatchVoteStatItemReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "orderId 不能为空")
    private Long orderId;

    @NotNull(message = "productId 不能为空")
    private Long productId;
}
