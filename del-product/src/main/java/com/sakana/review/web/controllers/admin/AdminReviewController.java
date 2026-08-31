package com.sakana.review.web.controllers.admin;

import com.sakana.review.dto.request.admin.ReviewSwitchReq;
import com.sakana.review.enums.ReviewErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.review.services.ReviewService;
import com.sakana.web.vo.R;
import com.sakana.review.web.vo.ReviewSwitchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 评价开关
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@Tag(name = "管理后台-评价", description = "👍/👎 评价开关控制")
public class AdminReviewController {

    private final ReviewService reviewService;

    @PutMapping("/praise")
    @Operation(summary = "开/关 👍 评价",
            description = "open=1 开启，open=0 关闭。关闭时用户仅不能提交/修改赞，组件仍渲染")
    public R<Void> setPraise(@Valid @RequestBody ReviewSwitchReq req) {
        checkOpen(req.getOpen());
        reviewService.setPraiseSwitch(req.getOpen() == 1);
        return R.ok();
    }

    @PutMapping("/bad")
    @Operation(summary = "开/关 👎 评价",
            description = "open=1 开启，open=0 关闭。关闭时用户仅不能提交/修改踩，组件仍渲染")
    public R<Void> setBad(@Valid @RequestBody ReviewSwitchReq req) {
        checkOpen(req.getOpen());
        reviewService.setBadSwitch(req.getOpen() == 1);
        return R.ok();
    }

    @GetMapping("/switch")
    @Operation(summary = "查询当前两开关状态")
    public R<ReviewSwitchVO> getSwitch() {
        return R.ok(reviewService.getSwitch());
    }

    private void checkOpen(Integer open) {
        if (open == null || (open != 0 && open != 1)) {
            throw new BizException(ReviewErrorCode.REVIEW_SWITCH_PARAM_INVALID);
        }
    }
}
