package com.sakana.services;

import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.web.vo.PaymentInternalVO;
import com.sakana.web.vo.PaymentStatusVO;
import com.sakana.web.vo.PaymentVO;

import java.util.Map;

/**
 * 支付服务接口（拆分后版本）。
 * <p>
 * 与单体原版的差异：
 * <ul>
 *   <li>{@link #createPayment} 通过 Feign 取订单快照，不再直接读 OrderMapper</li>
 *   <li>{@link #handleNotify} 仅写本地 t_payment + pay_outbox（同事务），不再反向写 OrderMapper / 不再 publishEvent</li>
 *   <li>新增 {@link #queryByOrderNo} 与 {@link #isOrderPaid}，供 del-order / del-comment 内部调用</li>
 * </ul>
 */
public interface PaymentService {

    /**
     * 创建支付（生成支付宝支付表单 payForm）
     */
    PaymentVO createPayment(Long userId, Long orderId);

    /**
     * 处理支付宝异步通知。
     * <p>
     * 拆分后行为：
     * <ol>
     *   <li>验签 + 幂等 + 金额校验</li>
     *   <li>更新本地 t_payment（同事务）</li>
     *   <li>INSERT pay_outbox（NEW 状态，同事务）</li>
     *   <li>返回 success / fail 给支付宝</li>
     * </ol>
     * 订单状态变更由 del-order 订阅 MQ 自行完成；邮件 / 站内信由 del-message 订阅 MQ 自行完成。
     */
    String handleNotify(Map<String, String> params);

    /**
     * 查询支付状态（对外：C 端 + 后台）
     */
    PaymentStatusVO queryStatus(Long userId, String orderNo);

    // ===== 内部 Feign 入口（仅内网） =====

    /**
     * 通过订单号查询支付记录（给 del-order / del-comment 调用）
     */
    PaymentInternalVO queryByOrderNo(String orderNo);

    /**
     * 简化布尔接口：订单是否已支付（给 del-comment 写评价前的校验使用）
     */
    boolean isOrderPaid(String orderNo);

    /**
     * 从 Redis 取订单快照（仅供 PayOutboxRelay / 测试使用）
     */
    OrderSnapshotVO getOrderSnapshot(String orderNo);

    /**
     * 根据订单号查订单 ID（用于支付同步跳转重定向到 /orders/{id}）
     * @param orderNo 订单号
     * @return 订单 ID，未找到返回 null
     */
    Long findOrderIdByOrderNo(String orderNo);
}


