package com.sakana.web.controllers;

import com.sakana.security.SecurityUtil;
import com.sakana.services.FreeOrderActivityService;
import com.sakana.services.FreeOrderGrabService;
import com.sakana.web.vo.FreeOrderActivityVO;
import com.sakana.web.vo.FreeOrderCouponVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/free-orders")
@RequiredArgsConstructor
@Tag(name = "抢免单", description = "C端用户抢免单码、查询我的免单码")
public class FreeOrderController {

    private final FreeOrderActivityService freeOrderActivityService;
    private final FreeOrderGrabService freeOrderGrabService;

    @GetMapping("/active")
    @Operation(summary = "查询当前可抢的活动")
    public R<List<FreeOrderActivityVO>> listActive() {
        return R.ok(freeOrderActivityService.listActive());
    }

    @PostMapping("/{activityId}/grab")
    @Operation(summary = "抢免单码")
    public R<FreeOrderCouponVO> grab(@PathVariable Long activityId) {
        Long userId = SecurityUtil.getCurrentUserId();
        FreeOrderCouponVO coupon = freeOrderGrabService.grab(activityId, userId);
        return R.ok(coupon);
    }

    @GetMapping("/my-coupons")
    @Operation(summary = "查询我的免单码")
    public R<List<FreeOrderCouponVO>> myCoupons() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(freeOrderGrabService.myCoupons(userId));
    }
}
