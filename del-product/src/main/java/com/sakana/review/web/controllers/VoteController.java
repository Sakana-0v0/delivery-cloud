package com.sakana.review.web.controllers;

import com.sakana.review.dto.request.VoteReq;
import com.sakana.security.SecurityUtil;
import com.sakana.review.services.ReviewService;
import com.sakana.web.vo.R;
import com.sakana.review.web.vo.VoteResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价投票接口（C 端）
 *
 * <p>路径挂在订单资源下：{@code POST /api/v1/orders/{orderId}/items/{productId}/vote}，
 * 由网关路由到 del-comment。
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "评价", description = "对订单商品点赞 / 点踩 / 取消")
public class VoteController {

    private final ReviewService reviewService;

    @PostMapping("/{orderId}/items/{productId}/vote")
    @Operation(summary = "对订单商品点赞/点踩/取消评价",
            description = "type=like 点赞 / bad 点踩 / null 取消；返回商品全局聚合计数 + 我的投票")
    public R<VoteResultVO> vote(@PathVariable Long orderId,
                                @PathVariable Long productId,
                                @RequestBody(required = false) VoteReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        String type = req == null ? null : req.getType();
        return R.ok(reviewService.vote(userId, orderId, productId, type));
    }
}