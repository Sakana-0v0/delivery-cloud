package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量评价统计请求（与 del-product 的 BatchVoteStatReq 字段对齐）
 */
@Data
public class BatchVoteStatReqVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前用户 ID（用于查 myVote，由 del-order 从 JWT 上下文传入） */
    private Long userId;

    private List<Item> orderItems;

    @Data
    public static class Item implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long orderId;
        private Long productId;
    }
}
