package com.sakana.web.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品评价聚合计数（用于缓存）
 */
@Data
public class ReviewCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 点踩数
     */
    private Integer dislikeCount;

    public static ReviewCountVO of(Integer likeCount, Integer dislikeCount) {
        ReviewCountVO vo = new ReviewCountVO();
        vo.setLikeCount(likeCount);
        vo.setDislikeCount(dislikeCount);
        return vo;
    }

    public static ReviewCountVO zero() {
        return of(0, 0);
    }
}
