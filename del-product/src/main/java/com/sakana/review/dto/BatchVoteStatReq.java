package com.sakana.review.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量投票统计入参：订单项列表
 *
 * <p>仅返回当前登录用户对这些订单项的投票数据 + 对应商品的全局聚合计数。
 */
@Data
public class BatchVoteStatReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前用户 ID（用于查 myVote，由 Feign 调用方从 JWT 上下文传入）
     * <p>不能用 SecurityUtil：内部服务调用时 SecurityContext 是 INTERNAL_SERVICE role，不是真实用户
     */
    private Long userId;

    @NotEmpty(message = "orderItems 不能为空")
    @Valid
    private List<BatchVoteStatItemReq> orderItems;
}
