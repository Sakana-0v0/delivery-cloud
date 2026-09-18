package com.sakana.review.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量投票统计出参项（单个订单项）
 *
 * <p>含：
 * <ul>
 *   <li>商品维度全局点赞/点踩数（跨订单、跨用户聚合）</li>
 *   <li>当前用户对该订单项的投票（like / bad / null）</li>
 * </ul>
 *
 * <p>字段命名对齐 {@link VoteResultVO}，避免出现 reviewType / myVote 同义不同名。
 */
@Data
public class BatchVoteStatItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long productId;

    /** 商品维度全局点赞数（跨订单、跨用户聚合） */
    private Integer likeCount;

    /** 商品维度全局点踩数（跨订单、跨用户聚合） */
    private Integer dislikeCount;

    /** 当前用户对该订单项的投票：like / bad / null（未投或已取消） */
    private String myVote;
}
