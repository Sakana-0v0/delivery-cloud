package com.sakana.review.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.sakana.review.dao.entity.Review;
import com.sakana.review.dao.mapper.ReviewMapper;

import com.sakana.review.enums.ReviewErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.review.services.ReviewCountCacheService;
import com.sakana.review.services.ReviewService;
import com.sakana.review.web.vo.ReviewCountVO;
import com.sakana.review.web.vo.ReviewSwitchVO;
import com.sakana.review.web.vo.ReviewVO;
import com.sakana.review.web.vo.VoteResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品售后评价服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    /** 评价开关 Redis Key 前缀 */
    private static final String SWITCH_KEY_PREFIX = "del-product:review:switch:";
    private static final String PRAISE_KEY = SWITCH_KEY_PREFIX + "praise";
    private static final String BAD_KEY = SWITCH_KEY_PREFIX + "bad";
    /** 开关永不过期（管理员手动维护） */
    private static final Duration SWITCH_TTL = Duration.ofDays(365);

    private final ReviewMapper reviewMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewCountCacheService reviewCountCacheService;

    @Override
    public List<ReviewVO> listMine(Long userId) {
        List<Review> list = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getIsDeleted, 0)
                        .orderByDesc(Review::getCreateTime));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ReviewSwitchVO getSwitch() {
        ReviewSwitchVO vo = new ReviewSwitchVO();
        // 默认值（key 不存在时）
        vo.setPraiseOpen(false);
        vo.setBadOpen(false);
        try {
            String praise = stringRedisTemplate.opsForValue().get(PRAISE_KEY);
            String bad = stringRedisTemplate.opsForValue().get(BAD_KEY);
            vo.setPraiseOpen("1".equals(praise));
            vo.setBadOpen("1".equals(bad));
        } catch (Exception e) {
            log.warn("[ReviewSwitch] Redis 不可用，返回默认关闭: {}", e.getMessage());
        }
        return vo;
    }

    @Override
    public void setPraiseSwitch(boolean open) {
        stringRedisTemplate.opsForValue().set(PRAISE_KEY, open ? "1" : "0", SWITCH_TTL);
        log.info("[ReviewSwitch] praise={}", open);
    }

    @Override
    public void setBadSwitch(boolean open) {
        stringRedisTemplate.opsForValue().set(BAD_KEY, open ? "1" : "0", SWITCH_TTL);
        log.info("[ReviewSwitch] bad={}", open);
    }

    @Override
    public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
        if (type != null && !"like".equals(type) && !"bad".equals(type)) {
            throw new BizException(ReviewErrorCode.REVIEW_VOTE_PARAM_INVALID);
        }

        ReviewSwitchVO sw = getSwitch();
        if ("like".equals(type) && !sw.isPraiseOpen()) {
            throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
        }
        if ("bad".equals(type) && !sw.isBadOpen()) {
            throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
        }

        // 1. 取当前投票（DB 主数据）
        Review existing = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getProductId, productId)
                        .eq(Review::getIsDeleted, 0));

        String oldVote = existing == null ? null : (existing.getType() == 1 ? "like" : "bad");

        // 2. 取消评价：物理删除 + 反向计数
        if (type == null) {
            if (existing != null) {
                reviewMapper.physicalDeleteById(existing.getId());
                if ("like".equals(oldVote)) {
                    reviewCountCacheService.incrementCount(productId, -1, 0);
                } else if ("bad".equals(oldVote)) {
                    reviewCountCacheService.incrementCount(productId, 0, -1);
                }
                reviewCountCacheService.invalidateLocal(productId);
            }
            reviewCountCacheService.invalidateUserVote(userId, orderId, productId);
            return buildVoteResult(productId, null);
        }

        // 3. 新投/改投
        if (oldVote == null) {
            // 新增
            Review r = new Review();
            r.setUserId(userId);
            r.setOrderId(orderId);
            r.setProductId(productId);
            r.setType("like".equals(type) ? 1 : 2);
            reviewMapper.insert(r);
            if ("like".equals(type)) {
                reviewCountCacheService.incrementCount(productId, 1, 0);
            } else {
                reviewCountCacheService.incrementCount(productId, 0, 1);
            }
        } else if (!oldVote.equals(type)) {
            // 改投：先回退旧计数，再增加新计数
            Review r = new Review();
            r.setId(existing.getId());
            r.setType("like".equals(type) ? 1 : 2);
            reviewMapper.updateById(r);

            if ("like".equals(oldVote)) {
                reviewCountCacheService.incrementCount(productId, -1, 0);
            } else {
                reviewCountCacheService.incrementCount(productId, 0, -1);
            }
            if ("like".equals(type)) {
                reviewCountCacheService.incrementCount(productId, 1, 0);
            } else {
                reviewCountCacheService.incrementCount(productId, 0, 1);
            }
        }
        // 同类型重复提交：幂等，无变化

        reviewCountCacheService.invalidateLocal(productId);
        reviewCountCacheService.invalidateUserVote(userId, orderId, productId);

        return buildVoteResult(productId, type);
    }

    private VoteResultVO buildVoteResult(Long productId, String myVote) {
        ReviewCountVO count = reviewCountCacheService.getCount(productId);
        VoteResultVO vo = new VoteResultVO();
        vo.setLikeCount(count.getLikeCount());
        vo.setDislikeCount(count.getDislikeCount());
        vo.setMyVote(myVote);
        return vo;
    }

    private ReviewVO toVO(Review r) {
        ReviewVO vo = new ReviewVO();
        BeanUtils.copyProperties(r, vo);
        return vo;
    }
}
