package com.sakana.review.web.controllers;


import com.sakana.review.services.ReviewService;
import com.sakana.web.vo.R;
import com.sakana.review.web.vo.ReviewSwitchVO;
import com.sakana.review.web.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.security.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品售后评价接口（C 端）
 * <p>
 * 点赞/点踩入口已迁移到订单资源 {@code POST /api/v1/orders/{id}/items/{productId}/vote}，
 * 本控制器仅保留「我的评价列表」与「开关状态」。
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "评价", description = "我的评价列表、开关状态")
public class ReviewController {

    private final ReviewService reviewService;

//    @GetMapping("/mine")
//    @Operation(summary = "我的评价列表")
//    public R<List<ReviewVO>> listMine() {
//        Long userId = SecurityUtil.getCurrentUserId();
//        return R.ok(reviewService.listMine(userId));
//    }

    @GetMapping("/switch")
    @Operation(summary = "查询 👍/👎 开关状态",
            description = "开关关闭时仅禁止提交/修改评价，组件仍正常渲染")
    public R<ReviewSwitchVO> getSwitch() {
        return R.ok(reviewService.getSwitch());
    }
}
