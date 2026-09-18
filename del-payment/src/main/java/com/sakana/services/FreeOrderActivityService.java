package com.sakana.services;

import com.sakana.web.vo.FreeOrderActivityVO;
import java.util.List;

/**
 * 抢免单活动管理服务
 */
public interface FreeOrderActivityService {

    /**
     * 创建活动（草稿状态）
     */
    Long createActivity(String name,
                        java.time.LocalDateTime startTime,
                        java.time.LocalDateTime grabTime,
                        java.time.LocalDateTime endTime,
                        Integer totalQuota,
                        java.math.BigDecimal maxFreeAmount,
                        Long adminId);

    /**
     * 发布活动（生成免单码）
     */
    void publishActivity(Long activityId);

    /**
     * 查询所有活动（含统计）
     */
    List<FreeOrderActivityVO> listAll();

    /**
     * 查询当前可抢的活动（C端用）
     */
    List<FreeOrderActivityVO> listActive();
}