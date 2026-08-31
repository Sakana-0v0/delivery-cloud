package com.sakana.review.dto;

import lombok.Data;

/**
 * 商品评价聚合计数投影（内部用，不对前端暴露）
 */
@Data
public class ReviewCountDTO {

    /** 商品ID */
    private Long productId;

    /** 评价类型：1 赞 2 踩 */
    private Integer type;

    /** 计数 */
    private Long cnt;
}
