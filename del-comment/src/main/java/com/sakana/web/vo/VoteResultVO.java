package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品点赞 / 点踩结果（对齐前端 VoteResult）
 */
@Data
public class VoteResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品维度全局点赞数（跨订单、跨用户聚合） */
    private Integer likeCount;

    /** 商品维度全局点踩数（跨订单、跨用户聚合） */
    private Integer dislikeCount;

    /** 当前用户对该订单商品的投票：like / bad / null（未投或已取消） */
    private String myVote;
}
