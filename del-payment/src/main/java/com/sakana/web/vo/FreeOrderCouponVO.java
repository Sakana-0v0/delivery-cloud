package com.sakana.web.vo;

import com.sakana.dao.entity.FreeOrderCoupon;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FreeOrderCouponVO {

    private Long id;
    private Long activityId;
    private String code;
    private BigDecimal maxAmount;
    private String status;
    private LocalDateTime grabbedTime;

    public static FreeOrderCouponVO from(FreeOrderCoupon coupon) {
        FreeOrderCouponVO vo = new FreeOrderCouponVO();
        vo.setId(coupon.getId());
        vo.setActivityId(coupon.getActivityId());
        vo.setCode(coupon.getCode());
        vo.setMaxAmount(coupon.getMaxAmount());
        vo.setStatus(coupon.getStatus());
        vo.setGrabbedTime(coupon.getGrabbedTime());
        return vo;
    }
}