package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.FreeOrderActivity;
import com.sakana.dao.entity.FreeOrderCoupon;
import com.sakana.dao.mapper.FreeOrderActivityMapper;
import com.sakana.dao.mapper.FreeOrderCouponMapper;
import com.sakana.enums.FreeOrderActivityStatus;
import com.sakana.enums.FreeOrderCouponStatus;
import com.sakana.exceptions.BizException;
import com.sakana.services.FreeOrderActivityService;
import com.sakana.web.vo.FreeOrderActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 免单活动 Service 实现。
 *
 * <p>★ P1-批量预热（#FREE-ORDER-006）：发布活动时不再逐条 INSERT，
 * 改为按 {@value #BATCH_INSERT_CHUNK_SIZE} 条/批调用
 * {@code couponMapper.batchInsert(...)}，单条 SQL 多值 INSERT。
 *
 * <p>万级免单码（10K）从 N 次 round-trip 降到 20 次（10K / 500），
 * 数据库连接池占用时间下降约 500 倍，活动上线瞬间不再打满连接池。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreeOrderActivityServiceImpl implements FreeOrderActivityService {

    /** ★ P1：批量预热单批大小（500 条/批 = 20 次 SQL 写入万级免单码） */
    private static final int BATCH_INSERT_CHUNK_SIZE = 500;

    private final FreeOrderActivityMapper activityMapper;
    private final FreeOrderCouponMapper couponMapper;

    @Override
    public Long createActivity(String name,
                               LocalDateTime startTime,
                               LocalDateTime grabTime,
                               LocalDateTime endTime,
                               Integer totalQuota,
                               BigDecimal maxFreeAmount,
                               Long adminId) {
        FreeOrderActivity activity = new FreeOrderActivity();
        activity.setName(name);
        activity.setStartTime(startTime);
        activity.setGrabTime(grabTime);
        activity.setEndTime(endTime);
        activity.setTotalQuota(totalQuota);
        activity.setMaxFreeAmount(maxFreeAmount);
        activity.setStatus(FreeOrderActivityStatus.DRAFT.code());
        activity.setCreatedBy(adminId);
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.insert(activity);
        log.info("[创建免单活动] id={}, name={}, quota={}", activity.getId(), name, totalQuota);
        return activity.getId();
    }

    @Override
    @Transactional
    public void publishActivity(Long activityId) {
        FreeOrderActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(com.sakana.enums.PayErrorCode.FREE_ORDER_ACTIVITY_NOT_FOUND);
        }
        if (!FreeOrderActivityStatus.DRAFT.code().equals(activity.getStatus())) {
            throw new BizException(com.sakana.enums.PayErrorCode.PAY_CREATE_FAIL, "活动状态不允许发布");
        }

        // 1. 内存中构造所有 coupon（避免边构造边写入阻塞数据库）
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int total = activity.getTotalQuota();
        List<FreeOrderCoupon> all = new ArrayList<>(total);
        for (int i = 1; i <= total; i++) {
            FreeOrderCoupon coupon = new FreeOrderCoupon();
            coupon.setActivityId(activityId);
            // %04d 支持万级序号
            coupon.setCode(String.format("FREE%s%04d", ts, i));
            coupon.setMaxAmount(activity.getMaxFreeAmount());
            coupon.setStatus(FreeOrderCouponStatus.AVAILABLE.code());
            all.add(coupon);
        }

        // 2. ★ P1-批量预热：分块写入数据库
        long startMs = System.currentTimeMillis();
        int totalBatches = (total + BATCH_INSERT_CHUNK_SIZE - 1) / BATCH_INSERT_CHUNK_SIZE;
        for (int batchIdx = 0; batchIdx < totalBatches; batchIdx++) {
            int from = batchIdx * BATCH_INSERT_CHUNK_SIZE;
            int to = Math.min(from + BATCH_INSERT_CHUNK_SIZE, total);
            List<FreeOrderCoupon> chunk = all.subList(from, to);
            couponMapper.batchInsert(chunk);
            log.info("[发布免单活动] 批量写入进度: {}/{}, chunk={}~{}",
                    batchIdx + 1, totalBatches, from + 1, to);
        }
        long costMs = System.currentTimeMillis() - startMs;
        log.info("[发布免单活动] 批量预热完成: id={}, total={}, batches={}, costMs={}",
                activityId, total, totalBatches, costMs);

        // 3. 更新活动状态为 PUBLISHED
        activity.setStatus(FreeOrderActivityStatus.PUBLISHED.code());
        activityMapper.updateById(activity);
        log.info("[发布免单活动] id={}, 状态更新为 PUBLISHED", activityId);
    }

    @Override
    public List<FreeOrderActivityVO> listAll() {
        List<FreeOrderActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<FreeOrderActivity>()
                        .orderByDesc(FreeOrderActivity::getCreateTime));
        return enrich(activities);
    }

    @Override
    public List<FreeOrderActivityVO> listActive() {
        LocalDateTime now = LocalDateTime.now();
        List<FreeOrderActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<FreeOrderActivity>()
                        .eq(FreeOrderActivity::getStatus, FreeOrderActivityStatus.PUBLISHED.code())
                        .le(FreeOrderActivity::getStartTime, now)
                        .ge(FreeOrderActivity::getEndTime, now)
                        .orderByAsc(FreeOrderActivity::getGrabTime));
        return enrich(activities);
    }

    private List<FreeOrderActivityVO> enrich(List<FreeOrderActivity> activities) {
        return activities.stream().map(activity -> {
            FreeOrderActivityVO vo = FreeOrderActivityVO.from(activity);
            long grabbed = couponMapper.selectCount(
                    new LambdaQueryWrapper<FreeOrderCoupon>()
                            .eq(FreeOrderCoupon::getActivityId, activity.getId())
                            .eq(FreeOrderCoupon::getStatus, FreeOrderCouponStatus.GRABBED.code()));
            long available = couponMapper.selectCount(
                    new LambdaQueryWrapper<FreeOrderCoupon>()
                            .eq(FreeOrderCoupon::getActivityId, activity.getId())
                            .eq(FreeOrderCoupon::getStatus, FreeOrderCouponStatus.AVAILABLE.code()));
            vo.setGrabbedCount((int) grabbed);
            vo.setAvailableCount((int) available);
            return vo;
        }).toList();
    }
}