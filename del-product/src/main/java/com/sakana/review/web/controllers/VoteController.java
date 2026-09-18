package com.sakana.review.web.controllers;

import com.sakana.review.dto.request.VoteReq;
import com.sakana.security.SecurityUtil;
import com.sakana.review.services.ReviewService;
import com.sakana.web.vo.R;
import com.sakana.review.web.vo.VoteResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价投票接口（C 端）
 *
 * <p>路径挂在订单资源下：
 * <ul>
 *   <li>{@code POST /api/v1/orders/{orderId}/items/{productId}/vote} —— 提交/切换/取消投票</li>
 *   <li>{@code GET  /api/v1/orders/{orderId}/items/{productId}/vote} —— 查询"我的当前投票" + 全局聚合计数</li>
 * </ul>
 * 由网关路由到 del-product。
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

    /**
     * 修复 #2 / BUG-010：查询我对该订单商品的当前投票状态
     * （前端用 GET 调，后端之前只暴露 POST → 405）
     *
     * @return 当前用户的投票（like/bad/null）+ 商品全局 likeCount / dislikeCount
     */
    @GetMapping("/{orderId}/items/{productId}/vote")
    @Operation(summary = "查询我对该订单商品的当前投票状态",
            description = "返回 likeCount / dislikeCount / myVote，不修改任何状态")
    public R<VoteResultVO> getMyVote(@PathVariable Long orderId,
                                      @PathVariable Long productId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(reviewService.getMyVote(userId, orderId, productId));
    }
}

