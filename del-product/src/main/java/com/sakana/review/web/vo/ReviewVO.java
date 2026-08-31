package com.sakana.review.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品售后评价视图对象
 */
@Data
public class ReviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;
    private Long productId;

    /** 评价类型（对齐前端契约）：like 赞 / bad 踩 */
    private String type;

    private LocalDateTime createTime;
}
