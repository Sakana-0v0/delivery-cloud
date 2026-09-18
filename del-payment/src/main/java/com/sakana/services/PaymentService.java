package com.sakana.services;

import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.web.vo.PaymentInternalVO;
import com.sakana.web.vo.PaymentStatusVO;
import com.sakana.web.vo.PaymentVO;

import java.util.Map;

/**
 * 支付服务接口（拆分后版本）。
 */
public interface PaymentService {

    /**
     * 创建支付（生成支付宝支付表单 payForm）。
     * @param freeOrderCode 免单码（可选），如有则订单0元免单
     */
    PaymentVO createPayment(Long userId, Long orderId, String freeOrderCode);

    /**
     * 处理支付宝异步通知。
     */
    String handleNotify(Map<String, String> params);

    /**
     * 查询支付状态（对外：C 端 + 后台）
     */
    PaymentStatusVO queryStatus(Long userId, String orderNo);

    // ===== 内部 Feign 入口（仅内网） =====

    /**
     * 通过订单号查询支付记录
     */
    PaymentInternalVO queryByOrderNo(String orderNo);

    /**
     * 简化布尔接口：订单是否已支付
     */
    boolean isOrderPaid(String orderNo);

    /**
     * 从 Redis 取订单快照
     */
    OrderSnapshotVO getOrderSnapshot(String orderNo);

    /**
     * 根据订单号查订单 ID
     */
    Long findOrderIdByOrderNo(String orderNo);
}