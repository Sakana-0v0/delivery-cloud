package com.sakana.web.vo;

import com.sakana.dao.entity.FreeOrderActivity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FreeOrderActivityVO {

    private Long id;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime grabTime;
    private LocalDateTime endTime;
    private Integer totalQuota;
    private BigDecimal maxFreeAmount;
    private String status;
    private Integer grabbedCount;
    private Integer availableCount;

    public static FreeOrderActivityVO from(FreeOrderActivity activity) {
        FreeOrderActivityVO vo = new FreeOrderActivityVO();
        vo.setId(activity.getId());
        vo.setName(activity.getName());
        vo.setStartTime(activity.getStartTime());
        vo.setGrabTime(activity.getGrabTime());
        vo.setEndTime(activity.getEndTime());
        vo.setTotalQuota(activity.getTotalQuota());
        vo.setMaxFreeAmount(activity.getMaxFreeAmount());
        vo.setStatus(activity.getStatus());
        return vo;
    }
}