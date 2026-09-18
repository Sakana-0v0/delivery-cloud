package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import com.sakana.dao.entity.FreeOrderActivity;
import com.sakana.dao.entity.FreeOrderCoupon;
import com.sakana.dao.mapper.FreeOrderActivityMapper;
import com.sakana.dao.mapper.FreeOrderCouponMapper;
import com.sakana.enums.FreeOrderActivityStatus;
import com.sakana.enums.FreeOrderCouponStatus;
import com.sakana.exceptions.BizException;
import com.sakana.metrics.CouponMetrics;
import com.sakana.services.FreeOrderGrabService;
import com.sakana.web.vo.FreeOrderCouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeOrderGrabServiceImpl implements FreeOrderGrabService {

    private final FreeOrderActivityMapper activityMapper;
    private final FreeOrderCouponMapper couponMapper;
    private final CouponMetrics couponMetrics;

    @Override
    @Transactional
    public FreeOrderCouponVO grab(Long activityId, Long userId) {
        long start = System.currentTimeMillis();
        try {
            return doGrab(activityId, userId, 0);
        } catch (BizException e) {
            // P3-MONITORING：按业务异常码分类计数
            String outcome = mapOutcome(e);
            couponMetrics.recordGrab(outcome, System.currentTimeMillis() - start);
            throw e;
        }
    }

    /**
     * 把 BizException 映射到监控 outcome（按异常 message 关键字匹配）。
     * BizException.getCode() 返回 int 拿不到枚举，所以走 message 关键字识别。
     */
    private String mapOutcome(BizException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("已抢")) return CouponMetrics.OUTCOME_ALREADY_GRABBED;
        if (msg.contains("名额") || msg.contains("抢光")) return CouponMetrics.OUTCOME_QUOTA_EXHAUSTED;
        if (msg.contains("不存在")) return CouponMetrics.OUTCOME_NOT_FOUND;
        if (msg.contains("不可用") || msg.contains("未开始") || msg.contains("已结束")) return CouponMetrics.OUTCOME_NOT_AVAILABLE;
        return "biz_error:" + (msg.length() > 30 ? msg.substring(0, 30) : msg);
    }

    private FreeOrderCouponVO doGrab(Long activityId, Long userId, int retryCount) {
        // P3-MONITORING：doGrab 入口时间戳（success 分支用）
        long doGrabStart = System.currentTimeMillis();
        FreeOrderActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_ACTIVITY_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getGrabTime()) || now.isAfter(activity.getEndTime())) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_NOT_AVAILABLE);
        }

        long existing = couponMapper.selectCount(
                new LambdaQueryWrapper<FreeOrderCoupon>()
                        .eq(FreeOrderCoupon::getActivityId, activityId)
                        .eq(FreeOrderCoupon::getGrabbedUserId, userId)
                        .in(FreeOrderCoupon::getStatus,
                                FreeOrderCouponStatus.GRABBED.code(),
                                FreeOrderCouponStatus.USED.code()));
        if (existing > 0) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_ALREADY_GRABBED);
        }

        List<FreeOrderCoupon> available = couponMapper.selectList(
                new LambdaQueryWrapper<FreeOrderCoupon>()
                        .eq(FreeOrderCoupon::getActivityId, activityId)
                        .eq(FreeOrderCoupon::getStatus, FreeOrderCouponStatus.AVAILABLE.code())
                        .last("LIMIT 1"));
        if (available.isEmpty()) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_QUOTA_EXHAUSTED);
        }

        FreeOrderCoupon target = available.get(0);
        int updated;
        try {
            updated = couponMapper.atomicGrab(target.getId(), userId);
        } catch (DuplicateKeyException e) {
            log.warn("[抢免单] UNIQUE 冲突（并发重复抢）activityId={}, userId={}, couponId={}",
                    activityId, userId, target.getId());
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_ALREADY_GRABBED);
        }
        if (updated == 0) {
            if (retryCount >= 1) {
                log.warn("[抢免单] 重试 1 次后仍失败 activityId={}, userId={}", activityId, userId);
                throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_QUOTA_EXHAUSTED);
            }
            log.info("[抢免单] 并发抢光，重试一次 activityId={}, userId={}", activityId, userId);
            return doGrab(activityId, userId, retryCount + 1);
        }

        FreeOrderCoupon coupon = couponMapper.selectById(target.getId());
        log.info("[抢免单] 成功 activityId={}, userId={}, code={}", activityId, userId, coupon.getCode());
        // P3-MONITORING：成功计数
        couponMetrics.recordGrab(CouponMetrics.OUTCOME_SUCCESS, System.currentTimeMillis() - doGrabStart);
        return FreeOrderCouponVO.from(coupon);
    }

    @Override
    @Transactional
    public boolean applyFreeOrder(String freeOrderCode, Long userId, Long orderId) {
        if (freeOrderCode == null || freeOrderCode.isBlank()) {
            return false;
        }
        int updated = couponMapper.atomicUse(freeOrderCode, userId, orderId);
        if (updated > 0) {
            log.info("[申请免单] 成功 code={}, userId={}, orderId={}", freeOrderCode, userId, orderId);
            return true;
        }
        FreeOrderCoupon coupon = couponMapper.selectOne(
                new LambdaQueryWrapper<FreeOrderCoupon>()
                        .eq(FreeOrderCoupon::getCode, freeOrderCode));
        if (coupon == null) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_CODE_INVALID);
        }
        if (!userId.equals(coupon.getGrabbedUserId())) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_CODE_NOT_YOURS);
        }
        throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_CODE_USED);
    }

    /**
     * BUG-007：根据免单码查免单额度
     */
    @Override
    public BigDecimal getFreeOrderMaxAmount(String freeOrderCode) {
        if (freeOrderCode == null || freeOrderCode.isBlank()) return null;
        FreeOrderCoupon coupon = couponMapper.selectOne(
                new LambdaQueryWrapper<FreeOrderCoupon>().eq(FreeOrderCoupon::getCode, freeOrderCode)
        );
        if (coupon == null) return null;
        return coupon.getMaxAmount();
    }

    /**
     * BUG-007：计算实付金额
     * 规则：订单金额 <= 免单额度 → 0 元；否则 → 订单 - 免单
     */
    @Override
    public BigDecimal calculateFinalAmount(BigDecimal orderAmount, BigDecimal freeAmount) {
        if (freeAmount == null) return orderAmount;
        if (orderAmount == null) return BigDecimal.ZERO;
        return orderAmount.compareTo(freeAmount) <= 0
                ? BigDecimal.ZERO
                : orderAmount.subtract(freeAmount);
    }

    @Override
    public List<FreeOrderCouponVO> myCoupons(Long userId) {
        List<FreeOrderCoupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<FreeOrderCoupon>()
                        .eq(FreeOrderCoupon::getGrabbedUserId, userId)
                        .in(FreeOrderCoupon::getStatus,
                                FreeOrderCouponStatus.GRABBED.code(),
                                FreeOrderCouponStatus.USED.code())
                        .orderByDesc(FreeOrderCoupon::getGrabbedTime));
        return coupons.stream().map(FreeOrderCouponVO::from).toList();
    }
}
