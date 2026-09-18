package com.sakana.review.services;

import com.sakana.review.web.vo.ReviewSwitchVO;
import com.sakana.review.web.vo.ReviewVO;
import com.sakana.review.web.vo.VoteResultVO;

import java.util.List;

/**
 * 商品售后评价服务
 *
 * <p>管理员通过两个全局开关控制用户能否提交/修改评价（操作锁，非组件显隐）。
 */
public interface ReviewService {

    /**
     * 我的评价列表（按订单独立）
     */
    List<ReviewVO> listMine(Long userId);

    /**
     * 查询两开关状态（前端据此置灰/启用操作，组件始终渲染）
     */
    ReviewSwitchVO getSwitch();

    /**
     * 设置 👍 开关
     */
    void setPraiseSwitch(boolean open);

    /**
     * 设置 👎 开关
     */
    void setBadSwitch(boolean open);

    /**
     * 对订单中的商品点赞 / 点踩 / 取消。
     *
     * @param userId    当前用户 ID
     * @param orderId   订单 ID
     * @param productId 商品 ID
     * @param type      "like" 赞 / "bad" 踩 / null 取消
     * @return 商品全局聚合计数 + 当前用户投票
     */
    VoteResultVO vote(Long userId, Long orderId, Long productId, String type);

    /**
     * 仅查询投票状态（不修改），用于前端 GET /orders/{orderId}/items/{productId}/vote
     * 修复 #2 / BUG-010：前端需要 GET 查询"我的当前投票"，返回 likeCount / dislikeCount / myVote
     */
    VoteResultVO getMyVote(Long userId, Long orderId, Long productId);

    /**
     * 批量获取订单项的投票统计（供 del-order 等服务一次拉取多个订单项数据，避免 N 次 RPC）
     *
     * @param userId 当前用户 ID（用于查 myVote）
     * @param items  订单项列表 [(orderId, productId)]
     * @return 与入参顺序一致的结果列表（含 likeCount/dislikeCount/myVote）
     */
    java.util.List<com.sakana.review.web.vo.BatchVoteStatItemVO> batchVoteStat(Long userId, java.util.List<com.sakana.review.dto.request.BatchVoteStatItemReq> items);
}

