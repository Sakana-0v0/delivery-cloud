package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.sakana.dao.entity.Review;
import com.sakana.dao.mapper.ReviewMapper;
import com.sakana.exceptions.BizException;
import com.sakana.services.ReviewCountCacheService;
import com.sakana.services.ReviewService;
import com.sakana.web.vo.ReviewCountVO;
import com.sakana.web.vo.ReviewSwitchVO;
import com.sakana.web.vo.ReviewVO;
import com.sakana.web.vo.VoteResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品售后评价服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String SWITCH_KEY_PREFIX = "bootfood:review:switch:";
    private static final String PRAISE_KEY = SWITCH_KEY_PREFIX + "praise";
    private static final String BAD_KEY = SWITCH_KEY_PREFIX + "bad";

    /** 评价类型（前端字符串契约） */
    private static final String TYPE_LIKE = "like";
    private static final String TYPE_BAD = "bad";

    /** 数据库评价类型：1 赞 2 踩 */
    private static final int DB_PRAISE = 1;
    private static final int DB_BAD = 2;

    /** 已完成（确认收货）订单状态 */
    private static final int ORDER_STATUS_FINISHED = 4;

    private final ReviewMapper reviewMapper;
//    private final OrderMapper orderMapper;
//    private final OrderItemMapper orderItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewCountCacheService reviewCountCacheService;
//    private final VoteProducer voteProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
        log.debug("[商品评价] vote 调用: userId={}, orderId={}, productId={}, type={}", userId, orderId, productId, type);


        return null;
    }

    @Override
    public List<ReviewVO> listMine(Long userId) {
        return List.of();
    }

    @Override
    public ReviewSwitchVO getSwitch() {
        return null;
    }

    @Override
    public void setPraiseSwitch(boolean open) {

    }

    @Override
    public void setBadSwitch(boolean open) {

    }
}
