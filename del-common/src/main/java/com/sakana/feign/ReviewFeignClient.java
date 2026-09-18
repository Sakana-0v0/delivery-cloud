package com.sakana.feign;

import com.sakana.feign.vo.BatchVoteStatReqVO;
import com.sakana.feign.vo.BatchVoteStatRespVO;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 评价服务 Feign 客户端（调用 del-product 的批量投票统计端点）
 *
 * <p>网关路由：{@code /api/v1/reviews/**} → del-product，所以路径直接写 {@code /batch-stats}。
 *
 * <p>供 del-order 在订单列表/详情组装时一次拉取所有订单项的 likeCount / dislikeCount / myVote，
 * 避免对每个 item 单独 RPC（N+1 问题）。
 */
@FeignClient(name = "del-product", contextId = "reviewFeignClient")
public interface ReviewFeignClient {

    /**
     * 批量获取订单项投票统计
     *
     * @param req 订单项列表 [{orderId, productId}]
     * @return 统一响应包装 results: [{orderId, productId, likeCount, dislikeCount, myVote}]
     */
    @PostMapping("/api/v1/internal/reviews/batch-stats")
    R<BatchVoteStatRespVO> batchStats(@RequestBody BatchVoteStatReqVO req);
}
