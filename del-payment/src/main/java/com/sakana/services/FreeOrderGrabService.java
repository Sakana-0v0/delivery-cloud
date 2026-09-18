package com.sakana.services;

import com.sakana.web.vo.FreeOrderCouponVO;
import java.math.BigDecimal;
import java.util.List;

/**
 * 抢免单服务（C端用户操作）
 */
public interface FreeOrderGrabService {

    /**
     * 抢单
     */
    FreeOrderCouponVO grab(Long activityId, Long userId);

    /**
     * 申请免单（支付时用）
     */
    boolean applyFreeOrder(String freeOrderCode, Long userId, Long orderId);

    /**
     * 查询用户当前活动中的免单码
     */
    List<FreeOrderCouponVO> myCoupons(Long userId);

    /**
     * BUG-007：根据免单码查询免单额度
     */
    BigDecimal getFreeOrderMaxAmount(String freeOrderCode);

    /**
     * BUG-007：根据订单金额和免单额度，计算实付金额
     * 规则：订单金额 <= 免单额度 → 0 元；否则 → 订单 - 免单
     */
    BigDecimal calculateFinalAmount(BigDecimal orderAmount, BigDecimal freeAmount);
}
