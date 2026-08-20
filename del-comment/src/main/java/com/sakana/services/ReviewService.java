package com.sakana.services;

import com.sakana.web.vo.ReviewSwitchVO;
import com.sakana.web.vo.ReviewVO;
import com.sakana.web.vo.VoteResultVO;

import java.util.List;

/**
 * 商品售后评价服务
 * <p>
 * 用户对已完成（status=4）订单中的商品进行 👍/👎 评价，评价按订单独立；
 * 管理员通过两个全局开关控制用户能否提交/修改评价（操作锁，非组件显隐）。
 */
public interface ReviewService {

    /**
     * 点赞 / 点踩 / 取消评价（对齐前端契约）。
     *
     * @param type      {@code "like"} 点赞 / {@code "bad"} 点踩 / {@code null} 取消
     * @return 商品全局聚合计数（likeCount / dislikeCount）+ 当前用户投票 myVote
     */
    VoteResultVO vote(Long userId, Long orderId, Long productId, String type);

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
}
