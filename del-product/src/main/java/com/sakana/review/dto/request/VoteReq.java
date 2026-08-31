package com.sakana.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品点赞 / 点踩请求（对齐前端契约）
 * <p>
 * type 取值：{@code "like"} 点赞 / {@code "bad"} 点踩 / {@code null} 取消评价。
 */
@Data
@Schema(description = "商品点赞/点踩请求")
public class VoteReq {

    @Schema(description = "评价类型：like 点赞 / bad 点踩 / null 取消",
            allowableValues = {"like", "bad"})
    private String type;
}
