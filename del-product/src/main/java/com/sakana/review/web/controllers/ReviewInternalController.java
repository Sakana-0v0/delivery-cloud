package com.sakana.review.web.controllers;

import com.sakana.review.dto.request.BatchVoteStatReq;
import com.sakana.review.services.ReviewService;
import com.sakana.review.web.vo.BatchVoteStatRespVO;
import com.sakana.security.SecurityUtil;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价 - 内部服务接口
 *
 * <p>{@code POST /api/v1/internal/reviews/batch-stats} 走 INTERNAL_SERVICE 鉴权链路，
 * 仅供 del-order 等服务一次性回填订单列表/详情里各订单项的 likeCount / dislikeCount / myVote，
 * 避免对每个 item 单独 RPC。
 *
 * <p>放在独立 controller 是为了避免 class-level @RequestMapping 路径和 method-level
 * 绝对路径在不同 Spring 版本里的合并行为差异。
 */
@RestController
@RequestMapping("/api/v1/internal/reviews")
@RequiredArgsConstructor
@Tag(name = "评价-内部", description = "内部服务调用：批量投票统计")
public class ReviewInternalController {

    private final ReviewService reviewService;

    @PostMapping("/batch-stats")
    @Operation(summary = "批量获取订单项投票统计（供订单列表/详情一次性回填赞/踩数据）",
            description = "入参 orderItems: [{orderId, productId}, ...]；返回 results: [{orderId, productId, likeCount, dislikeCount, myVote}, ...]")
    public R<BatchVoteStatRespVO> batchStats(@Valid @RequestBody BatchVoteStatReq req) {
        // 内部服务调用：从请求体里取 userId（不能用 SecurityUtil，因为是 Feign 内部调用，上下文是 INTERNAL_SERVICE 而不是用户）
        Long userId = req.getUserId();
        BatchVoteStatRespVO resp = new BatchVoteStatRespVO();
        resp.setResults(reviewService.batchVoteStat(userId, req.getOrderItems()));
        return R.ok(resp);
    }
}
