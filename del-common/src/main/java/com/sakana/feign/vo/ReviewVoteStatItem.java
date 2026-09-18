package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价统计项（与 del-product 的 BatchVoteStatItemVO 字段对齐，供 del-order 等调用方使用）
 */
@Data
public class ReviewVoteStatItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long productId;
    private Integer likeCount;
    private Integer dislikeCount;
    /** "like" / "bad" / null */
    private String myVote;
}
