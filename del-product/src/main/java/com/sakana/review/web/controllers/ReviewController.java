package com.sakana.review.web.controllers;

import com.sakana.review.dto.request.BatchVoteStatReq;
import com.sakana.review.services.ReviewService;
import com.sakana.review.web.vo.BatchVoteStatRespVO;
import com.sakana.review.web.vo.ReviewSwitchVO;
import com.sakana.review.web.vo.ReviewVO;
import com.sakana.security.SecurityUtil;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品售后评价接口（C 端）
 *
 * <p>点赞/点踩入口已迁移到订单资源 {@code POST /api/v1/orders/{id}/items/{productId}/vote}，
 * 本控制器保留「批量投票统计」「我的评价列表」与「开关状态」。
 *
 * <p>{@code POST /api/v1/internal/reviews/batch-stats} 走 INTERNAL_SERVICE 鉴权链路，
 * 仅供 del-order 等服务一次性回填订单列表/详情里
 * 各订单项的 likeCount / dislikeCount / myVote，避免对每个 item 单独 RPC。
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "评价", description = "我的评价列表、开关状态、批量投票统计")
public class ReviewController {

    private final ReviewService reviewService;


    @GetMapping("/mine")
    @Operation(summary = "我的评价列表")
    public R<List<ReviewVO>> listMine() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(reviewService.listMine(userId));
    }

    @GetMapping("/switch")
    @Operation(summary = "查询 👍/👎 开关状态",
            description = "开关关闭时仅禁止提交/修改评价，组件仍正常渲染")
    public R<ReviewSwitchVO> getSwitch() {
        return R.ok(reviewService.getSwitch());
    }
}
