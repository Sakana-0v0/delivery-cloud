package com.sakana.review.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.sakana.review.dao.entity.Review;
import com.sakana.review.dao.entity.ReviewLog;
import com.sakana.review.dao.mapper.ReviewLogMapper;
import com.sakana.review.dao.mapper.ReviewMapper;

import com.sakana.review.enums.ReviewAction;
import com.sakana.review.enums.ReviewErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.review.mq.VoteProducer;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品售后评价服务实现
 *
 * <p>评价投票状态机设计（对齐原单体设计 v1.0）：
 * - Redis 是用户视角的权威，所有计数写操作走同步 Redis Lua
 * - t_review_log 事件日志同步记录，作为管理员面板数据源和审计追溯
 * - t_review 表通过 MQ 异步落库（#PROD-VOTE-010），响应时间回到 ~5ms
 *
 * <p>全部 6 种状态转移：
 *   ① (无) + like       → INSERT t_review_log + Redis(+1,0) + MQ(vote_persist)
 *   ② (无) + bad        → INSERT t_review_log + Redis(0,+1) + MQ(vote_persist)
 *   ③ 已赞 + like（再点）→ INSERT t_review_log + Redis(-1,0) + MQ(vote_persist)  （toggle取消）
 *   ④ 已踩 + bad（再点）→ INSERT t_review_log + Redis(0,-1) + MQ(vote_persist)  （toggle取消）
 *   ⑤ 已赞 + bad（切换）→ INSERT t_review_log + Redis(-1,+1) + MQ(vote_persist)
 *   ⑥ 已踩 + like（切换）→ INSERT t_review_log + Redis(+1,-1) + MQ(vote_persist)
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
    private final ReviewLogMapper reviewLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewCountCacheService reviewCountCacheService;
    private final VoteProducer voteProducer;

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

    /**
     * 评价投票核心方法 — 完整状态机实现（#PROD-VOTE-008 + #009 + #010）
     *
     * <p>响应时间：~5ms（不等 DB，MQ 异步落库）
     *
     * <p>状态转移表：
     * <pre>
     *  旧状态 \ 新操作  like          bad           null(取消)
     *  ─────────────────────────────────────────────────────
     *  (无)              ① INSERT    ② INSERT       ⑨ no-op
     *                     (+1,0)     (0,+1)         (0,0)
     *                     log+MQ     log+MQ
     *
     *  已赞 (like)        ③ DELETE   ⑤ UPDATE      ⑦ DELETE
     *                     (-1,0)     (-1,+1)        (-1,0)
     *                     log+MQ     log+MQ         log+MQ
     *
     *  已踩 (bad)         ⑥ UPDATE   ④ DELETE      ⑧ DELETE
     *                     (+1,-1)    (0,-1)         (0,-1)
     *                     log+MQ     log+MQ         log+MQ
     * </pre>
     */
    @Override
    public VoteResultVO vote(Long userId, Long orderId, Long productId, String type) {
        // 1. 参数校验
        if (type != null && !"like".equals(type) && !"bad".equals(type)) {
            throw new BizException(ReviewErrorCode.REVIEW_VOTE_PARAM_INVALID);
        }

        // 2. 开关校验
        ReviewSwitchVO sw = getSwitch();
        if ("like".equals(type) && !sw.isPraiseOpen()) {
            throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
        }
        if ("bad".equals(type) && !sw.isBadOpen()) {
            throw new BizException(ReviewErrorCode.REVIEW_SWITCH_CLOSED);
        }

        // 3. 查询当前投票状态（同步，用于计算 delta）
        Review existing = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getProductId, productId)
                        .eq(Review::getIsDeleted, 0));

        String oldVote = existing == null ? null : (existing.getType() == 1 ? "like" : "bad");
        Integer oldAction = oldVote == null ? null : ("like".equals(oldVote) ? 1 : 2);
        LocalDateTime now = LocalDateTime.now();

        // 4. 根据状态机执行转移
        if (type == null) {
            // ── 取消操作 ───────────────────────────────────
            if (existing != null) {
                int deltaLike = existing.getType() == 1 ? -1 : 0;
                int deltaBad  = existing.getType() == 2 ? -1 : 0;

                // 同步 Redis（立即可见）
                syncRedisCount(productId, deltaLike, deltaBad);

                // 同步写事件日志（append-only）
                int cancelAction = existing.getType() == 1
                        ? ReviewAction.CANCEL_LIKE.getCode()
                        : ReviewAction.CANCEL_BAD.getCode();
                insertLog(userId, orderId, productId, cancelAction, oldAction, now);

                // 异步 MQ 落 DB（不等 Consumer 返回）
                voteProducer.sendVotePersistEvent(userId, orderId, productId, null, oldVote);

                log.info("[投票] 取消 vote: userId={}, productId={}, oldVote={}", userId, productId, oldVote);
            }
            // ⑨：无记录，取消是 no-op，不写 log 和 MQ

        } else if (oldVote == null) {
            // ── 新增投票 ─────────────────────────────────
            int deltaLike = "like".equals(type) ? 1 : 0;
            int deltaBad  = "bad".equals(type)  ? 1 : 0;

            // 同步 Redis（立即可见）
            syncRedisCount(productId, deltaLike, deltaBad);

            // 同步写事件日志
            int newAction = "like".equals(type)
                    ? ReviewAction.LIKE.getCode()
                    : ReviewAction.BAD.getCode();
            insertLog(userId, orderId, productId, newAction, null, now);

            // 异步 MQ 落 DB
            voteProducer.sendVotePersistEvent(userId, orderId, productId, type, null);

            log.info("[投票] 新增 vote: userId={}, productId={}, type={}", userId, productId, type);

        } else if (oldVote.equals(type)) {
            // ── Toggle 取消（同类型再点）────────────────────
            int deltaLike = "like".equals(type) ? -1 : 0;
            int deltaBad  = "bad".equals(type)  ? -1 : 0;

            // 同步 Redis
            syncRedisCount(productId, deltaLike, deltaBad);

            // 同步写事件日志
            int cancelAction = "like".equals(type)
                    ? ReviewAction.CANCEL_LIKE.getCode()
                    : ReviewAction.CANCEL_BAD.getCode();
            insertLog(userId, orderId, productId, cancelAction, oldAction, now);

            // 异步 MQ 落 DB
            voteProducer.sendVotePersistEvent(userId, orderId, productId, null, oldVote);

            log.info("[投票] Toggle取消 vote: userId={}, productId={}, oldVote={}", userId, productId, oldVote);

        } else {
            // ── 切换投票（改投）──────────────────────────
            int deltaLike = "like".equals(type) ? 1 : -1;
            int deltaBad  = "bad".equals(type)  ? 1 : -1;

            // 同步 Redis
            reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
            reviewCountCacheService.invalidateLocal(productId);
            reviewCountCacheService.publishInvalidate(productId);

            // 同步写事件日志
            int changeAction = "like".equals(type)
                    ? ReviewAction.CHANGE_TO_LIKE.getCode()
                    : ReviewAction.CHANGE_TO_BAD.getCode();
            insertLog(userId, orderId, productId, changeAction, oldAction, now);

            // 异步 MQ 落 DB
            voteProducer.sendVotePersistEvent(userId, orderId, productId, type, oldVote);

            log.info("[投票] 切换 vote: userId={}, productId={}, {}→{}", userId, productId, oldVote, type);
        }

        // 5. 失效用户投票缓存
        reviewCountCacheService.invalidateUserVote(userId, orderId, productId);

        // 6. 返回当前投票状态
        String myVote;
        if (type == null) {
            myVote = null;
        } else if (oldVote != null && oldVote.equals(type)) {
            // toggle 取消后，当前无投票
            myVote = null;
        } else {
            myVote = type;
        }

        return buildVoteResult(productId, myVote);
    }

    /**
     * 同步更新 Redis 聚合计数（三级缓存 write-through）
     */
    private void syncRedisCount(Long productId, int deltaLike, int deltaBad) {
        if (deltaLike == 0 && deltaBad == 0) {
            return;
        }
        reviewCountCacheService.incrementCount(productId, deltaLike, deltaBad);
        reviewCountCacheService.invalidateLocal(productId);
        reviewCountCacheService.publishInvalidate(productId);
    }

    /**
     * 插入评价事件日志（t_review_log，append-only）
     */
    private void insertLog(Long userId, Long orderId, Long productId,
                           int action, Integer oldAction, LocalDateTime eventTime) {
        try {
            ReviewLog reviewLog = new ReviewLog();
            reviewLog.setUserId(userId);
            reviewLog.setOrderId(orderId);
            reviewLog.setProductId(productId);
            reviewLog.setAction(action);
            reviewLog.setOldAction(oldAction);
            reviewLog.setEventTime(eventTime);
            reviewLogMapper.insert(reviewLog);
            log.debug("[投票日志] 写入 eventLog: action={}, oldAction={}, productId={}",
                    action, oldAction, productId);
        } catch (Exception e) {
            log.warn("[投票日志] 写入失败: userId={}, productId={}, action={}, error={}",
                    userId, productId, action, e.getMessage());
        }
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
