package com.sakana.services.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sakana.configs.alipay.AlipayProperties;
import com.sakana.dao.entity.PayOutbox;
import com.sakana.dao.entity.Payment;
import com.sakana.dao.mapper.PayOutboxMapper;
import com.sakana.dao.mapper.PaymentMapper;
import com.sakana.enums.PayErrorCode;
import com.sakana.enums.PayOutboxStatus;
import com.sakana.enums.PayStatus;
import com.sakana.events.OrderPaidPayload;
import com.sakana.exceptions.BizException;
import com.sakana.feign.OrderClient;
import com.sakana.feign.UserClient;
import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.services.PaymentService;
import com.sakana.web.vo.PaymentInternalVO;
import com.sakana.web.vo.PaymentStatusVO;
import com.sakana.web.vo.PaymentVO;
import com.sakana.web.vo.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务实现（拆分后核心改造版本）。
 *
 * <h2>解耦要点</h2>
 * <ul>
 *   <li><b>原耦合 ①</b>：handleNotify 直接调 orderMapper.updateById 改订单状态 —— 现已删除，改为 INSERT pay_outbox，订单状态由 del-order 订阅 MQ 自行更新</li>
 *   <li><b>原耦合 ②</b>：handleNotify 直接调 userMapper.selectById 取用户名/邮箱 —— 现已删除，用户信息走创建支付时缓存到 Redis 的快照</li>
 *   <li><b>原耦合 ③</b>：调用 OrderStatus.of(...).canPay() 校验 —— 现已删除，由 del-order 暴露的 OrderSnapshotVO.canPay 字段替代</li>
 *   <li><b>原耦合 ④</b>：publishEvent(OrderPaidEvent) —— 现已删除，改用 PayOutboxRelay → RabbitMQ 广播</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** Redis key 前缀：订单快照（userId + username + email 等），TTL 30 分钟 */
    private static final String SNAPSHOT_KEY_PREFIX = "pay:order-snapshot:";
    private static final long SNAPSHOT_TTL_MINUTES = 30;

    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;

    // ★ 与原版相比，删除了 OrderMapper、UserMapper、ApplicationEventPublisher
    private final PaymentMapper paymentMapper;
    private final PayOutboxMapper outboxMapper;
    private final OrderClient orderClient;          // ★ Feign 替代 OrderMapper
    private final UserClient userClient;            // ★ Feign 兜底替代 UserMapper
    private final RedisTemplate<String, OrderSnapshotVO> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    // ====================================================================
    // 1. createPayment
    // ====================================================================
    @Override
    public PaymentVO createPayment(Long userId, Long orderId) {
        log.info("[创建支付] ========== START ==========");
        log.info("[创建支付] userId={}, orderId={}", userId, orderId);

        log.info("========== [创建支付] 支付宝配置传输明细 ==========");
        log.info("[配置传输] gateway     = {}", alipayProperties.getGateway());
        log.info("[配置传输] appId       = {}", alipayProperties.getAppId());
        log.info("[配置传输] notifyUrl   = {}", alipayProperties.getNotifyUrl());
        log.info("[配置传输] returnUrl   = {}", alipayProperties.getReturnUrl());
        log.info("====================================================");

        // 1. ★ 通过 Feign 取订单快照（替代原 orderMapper.selectById）
        OrderSnapshotVO order = fetchOrderSnapshot(orderId);
        log.info("[创建支付] 订单快照: orderNo={}, status={}, payAmount={}, userId={}",
                order.getOrderNo(), order.getStatus(), order.getPayAmount(), order.getUserId());

        // 2. 鉴权 + 可付性校验（用 del-order 返回的 canPay，不再调 OrderStatus.of）
        if (!order.getUserId().equals(userId)) {
            log.warn("[创建支付] 订单不属于该用户: orderId={}, userId={}, orderUserId={}",
                    orderId, userId, order.getUserId());
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL,
                    "无权访问此订单");
        }
        if (Boolean.FALSE.equals(order.getCanPay())) {
            log.warn("[创建支付] 订单不可支付: orderId={}, status={}, canPay={}",
                    orderId, order.getStatus(), order.getCanPay());
            throw new BizException(PayErrorCode.PAY_ALREADY_PAID);
        }

        // 3. 生成支付流水号
        String payNo = UUID.randomUUID().toString().replace("-", "");
        log.info("[创建支付] 生成支付流水号: payNo={}", payNo);

        // 4. 写入支付记录（待支付）
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setPayNo(payNo);
        payment.setChannel("alipay");
        payment.setAmount(order.getPayAmount());
        payment.setStatus(PayStatus.PENDING.code());
        paymentMapper.insert(payment);
        log.info("[创建支付] 支付记录已写入: id={}, payNo={}, amount={}",
                payment.getId(), payNo, order.getPayAmount());

        // 5. ★ 缓存订单快照到 Redis（替代回调时读 UserMapper）
        cacheOrderSnapshot(order);

        // 6. 调用支付宝 SDK 生成支付表单
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(alipayProperties.getReturnUrl());
        request.setNotifyUrl(alipayProperties.getNotifyUrl());

        Map<String, Object> bizMap = new LinkedHashMap<>();
        bizMap.put("out_trade_no", payNo);
        bizMap.put("total_amount", order.getPayAmount().toString());
        bizMap.put("subject", "外卖订单-" + order.getOrderNo());
        bizMap.put("product_code", "FAST_INSTANT_TRADE_PAY");
        String bizContent;
        try {
            bizContent = objectMapper.writeValueAsString(bizMap);
        } catch (JsonProcessingException e) {
            log.error("[创建支付] bizContent 序列化失败: orderId={}", orderId, e);
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL);
        }
        request.setBizContent(bizContent);

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            log.info("[创建支付] 支付宝SDK响应: isSuccess={}, code={}, msg={}, subCode={}",
                    response.isSuccess(), response.getCode(), response.getMsg(), response.getSubCode());

            if (response.isSuccess()) {
                PaymentVO vo = new PaymentVO();
                vo.setOrderNo(order.getOrderNo());
                vo.setPayForm(response.getBody());
                log.info("[创建支付] ========== SUCCESS ==========");
                return vo;
            } else {
                log.error("[创建支付] ========== FAIL ========== code={}, msg={}",
                        response.getCode(), response.getMsg());
                throw new BizException(PayErrorCode.PAY_CREATE_FAIL);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[创建支付] ========== EXCEPTION ==========", e);
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL);
        }
    }

    // ====================================================================
    // 2. handleNotify —— ★ 核心改造
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params) {
        log.info("[支付回调] [Controller] 收到回调, params={}", params);

        String payNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");
        String tradeNo = params.get("trade_no");
        log.info("[支付回调] 关键参数: payNo={}, tradeStatus={}, totalAmount={}, tradeNo={}",
                payNo, tradeStatus, totalAmount, tradeNo);

        // 1. 验签（Alipay SDK 留在本服务，不外泄）
        try {
            boolean signCheck = AlipaySignature.rsaCheckV1(
                    params, alipayProperties.getPublicKey(), "UTF-8", "RSA2");
            log.info("[支付回调] 验签结果: {}", signCheck);
            if (!signCheck) {
                log.warn("[支付回调] 验签失败: payNo={}", payNo);
                return "fail";
            }
        } catch (Exception e) {
            log.error("[支付回调] 验签异常: payNo={}", payNo, e);
            return "fail";
        }

        // 2. 查询本地支付记录
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
        if (payment == null) {
            log.warn("[支付回调] 支付记录不存在: payNo={}", payNo);
            return "fail";
        }
        log.info("[支付回调] 支付记录状态: status={}, amount={}", payment.getStatus(), payment.getAmount());

        // 3. 幂等
        if (payment.getStatus() != null && payment.getStatus() == PayStatus.SUCCESS.code()) {
            log.info("[支付回调] 幂等跳过: payNo={} 已成功, 不再处理", payNo);
            return "success";
        }

        // 4. 交易状态检查
        if (!("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))) {
            log.info("[支付回调] 交易状态非成功: payNo={}, tradeStatus={}", payNo, tradeStatus);
            return "fail";
        }

        // 5. 金额校验
        if (totalAmount != null && payment.getAmount().compareTo(new BigDecimal(totalAmount)) != 0) {
            log.error("[支付回调] 金额不匹配: payNo={}, expected={}, actual={}",
                    payNo, payment.getAmount(), totalAmount);
            return "fail";
        }

        // 6. ★ 更新本地支付记录（同事务）
        payment.setStatus(PayStatus.SUCCESS.code());
        payment.setPaidAt(LocalDateTime.now());
        payment.setTradeNo(tradeNo);
        payment.setRawNotify(params.toString());
        paymentMapper.updateById(payment);
        log.info("[支付回调] 支付记录已更新: status=1, paidAt={}", payment.getPaidAt());

        // 7. ★ 写 outbox（同事务，事务提交后由 PayOutboxRelay 投递到 MQ）
        //    ★ 替代原 orderMapper.updateById(改订单状态) + userMapper.selectById(取用户)
        //       + eventPublisher.publishEvent(OrderPaidEvent) 三步
        OrderSnapshotVO snapshot = getOrderSnapshot(payment.getOrderNo());
        if (snapshot == null) {
            // 极端情况：Redis 快照已过期（30 分钟外），fallback 到 del-user Feign 取 email
            log.warn("[支付回调] 订单快照已过期, orderNo={}, 走兜底 Feign",
                    payment.getOrderNo());
            snapshot = new OrderSnapshotVO();
            snapshot.setOrderNo(payment.getOrderNo());
            snapshot.setUserId(extractUserIdFromPayment(payment));
            try {
                if (snapshot.getUserId() != null) {
                    Map<String, Object> contact = userClient
                            .getUserContact(snapshot.getUserId()).getData();
                    if (contact != null) {
                        snapshot.setUsername((String) contact.get("username"));
                        snapshot.setEmail((String) contact.get("email"));
                    }
                }
            } catch (Exception ex) {
                log.warn("[支付回调] UserClient 兜底失败（不影响支付结果）: {}", ex.toString());
            }
        }

        String eventId = UUID.randomUUID().toString();
        PayOutbox outbox = new PayOutbox();
        outbox.setEventId(eventId);
        outbox.setPayNo(payNo);
        outbox.setOrderNo(payment.getOrderNo());
        outbox.setUserId(snapshot.getUserId());
        outbox.setUsername(snapshot.getUsername());
        outbox.setEmail(snapshot.getEmail());
        outbox.setPayAmount(payment.getAmount());
        outbox.setPaidAt(payment.getPaidAt());
        outbox.setTradeNo(tradeNo);
        outbox.setStatus(PayOutboxStatus.NEW.code());
        outbox.setRetryCount(0);
        outboxMapper.insert(outbox);
        log.info("[支付回调] outbox 已写入: eventId={}, orderNo={}", eventId, payment.getOrderNo());

        log.info("[支付回调] ========== SUCCESS ==========");
        return "success";
    }

    /**
     * 兜底逻辑：尝试从 Redis 拿快照以获取 userId；拿不到则返回 null（不影响支付主流程）。
     * <p>
     * 本服务不持有 t_order，无法 selectById(orderId)。这是"不允许批量调用"原则的合法单次例外。
     */
    private Long extractUserIdFromPayment(Payment payment) {
        OrderSnapshotVO cached = getOrderSnapshot(payment.getOrderNo());
        return cached != null ? cached.getUserId() : null;
    }

    // ====================================================================
    // 3. queryStatus
    // ====================================================================
    @Override
    public PaymentStatusVO queryStatus(Long userId, String orderNo) {
        log.info("[查询状态] userId={}, orderNo={}", userId, orderNo);

        // 1. 用支付记录的 orderId 反查订单做归属校验（替代直接 orderMapper.selectOne(orderNo)）
        OrderSnapshotVO order = null;
        try {
            Payment paymentForCheck = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
            if (paymentForCheck != null) {
                order = fetchOrderSnapshot(paymentForCheck.getOrderId());
            }
        } catch (Exception e) {
            log.warn("[查询状态] 取订单快照失败（不影响主流程）: {}", e.toString());
        }

        log.info("[查询状态] 订单查询: orderNo={}, found={}", orderNo, order != null);

        if (order == null) {
            log.warn("[查询状态] 订单不存在: orderNo={}", orderNo);
            throw new BizException(PayErrorCode.PAY_NOT_FOUND,
                    "订单不存在或尚未发起支付");
        }
        if (!order.getUserId().equals(userId)) {
            log.warn("[查询状态] 订单不属于该用户: orderNo={}, userId={}, orderUserId={}",
                    orderNo, userId, order.getUserId());
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL, "无权访问此订单");
        }

        // 2. 查询本地支付记录
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        log.info("[查询状态] 支付记录查询: orderNo={}, found={}, status={}, amount={}",
                orderNo, payment != null,
                payment != null ? payment.getStatus() : "N/A",
                payment != null ? payment.getAmount() : "N/A");

        PaymentStatusVO vo = new PaymentStatusVO();
        vo.setOrderNo(orderNo);
        if (payment != null) {
            vo.setPayNo(payment.getPayNo());
            vo.setStatus(payment.getStatus());
            vo.setPaidAt(payment.getPaidAt());
            vo.setAmount(payment.getAmount());
        } else {
            vo.setStatus(PayStatus.PENDING.code());
        }

        // 3. 调用支付宝 API 查询真实交易状态（保留原版逻辑）
        if (payment != null && payment.getPayNo() != null) {
            try {
                AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
                Map<String, Object> queryMap = new LinkedHashMap<>();
                queryMap.put("out_trade_no", payment.getPayNo());
                queryRequest.setBizContent(objectMapper.writeValueAsString(queryMap));

                AlipayTradeQueryResponse queryResponse = alipayClient.execute(queryRequest);
                log.info("[查询状态] [支付宝查询] isSuccess={}, code={}, msg={}, tradeStatus={}",
                        queryResponse.isSuccess(), queryResponse.getCode(),
                        queryResponse.getMsg(), queryResponse.getTradeStatus());

                if (queryResponse.isSuccess() && queryResponse.getTradeStatus() != null) {
                    vo.setTradeNo(queryResponse.getTradeNo());
                    vo.setBuyerUserId(queryResponse.getBuyerUserId());
                    vo.setBuyerLogonId(queryResponse.getBuyerLogonId());

                    String alipayTradeStatus = queryResponse.getTradeStatus();
                    if ("TRADE_SUCCESS".equals(alipayTradeStatus)
                            || "TRADE_FINISHED".equals(alipayTradeStatus)) {
                        if (payment.getStatus() == null
                                || payment.getStatus() != PayStatus.SUCCESS.code()) {
                            payment.setStatus(PayStatus.SUCCESS.code());
                            payment.setTradeNo(queryResponse.getTradeNo());
                            payment.setPaidAt(LocalDateTime.now());
                            paymentMapper.updateById(payment);
                            log.info("[查询状态] [支付宝查询] 已同步支付状态到本地: payNo={}",
                                    payment.getPayNo());
                        }
                        vo.setStatus(PayStatus.SUCCESS.code());
                    } else if ("WAIT_BUYER_PAY".equals(alipayTradeStatus)) {
                        vo.setStatus(PayStatus.PENDING.code());
                    }
                }
            } catch (Exception e) {
                log.error("[查询状态] [支付宝查询] 异常: orderNo={}", orderNo, e);
            }
        }

        log.info("[查询状态] 返回: orderNo={}, payStatus={}", orderNo, vo.getStatus());
        return vo;
    }

    // ====================================================================
    // 4. 内部 API（Feign 入口）
    // ====================================================================
    @Override
    public PaymentInternalVO queryByOrderNo(String orderNo) {
        log.info("[内部API] queryByOrderNo orderNo={}", orderNo);
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        return PaymentInternalVO.from(payment);
    }

    @Override
    public boolean isOrderPaid(String orderNo) {
        log.info("[内部API] isOrderPaid orderNo={}", orderNo);
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        return payment != null
                && payment.getStatus() != null
                && payment.getStatus() == PayStatus.SUCCESS.code();
    }

    @Override
    public OrderSnapshotVO getOrderSnapshot(String orderNo) {
        try {
            return redisTemplate.opsForValue().get(SNAPSHOT_KEY_PREFIX + orderNo);
        } catch (Exception e) {
            log.warn("[Redis] 读快照失败: orderNo={}, err={}", orderNo, e.toString());
            return null;
        }
    }

    // ====================================================================
    // 私有辅助
    // ====================================================================

    /**
     * 通过 Feign 获取订单快照（同步、强一致）。fallback 由 OrderClientFallback 处理。
     */
    private OrderSnapshotVO fetchOrderSnapshot(Long orderId) {
        R<OrderSnapshotVO> resp = orderClient.getOrderForPayment(orderId);
        if (resp == null || resp.getCode() == null || resp.getCode() != 0 || resp.getData() == null) {
            String msg = resp == null ? "no response" :
                    ("code=" + resp.getCode() + ", msg=" + resp.getMessage());
            log.error("[Feign] OrderClient.getOrderForPayment 失败: orderId={}, {}", orderId, msg);
            throw new BizException(PayErrorCode.ORDER_SERVICE_UNAVAILABLE);
        }
        return resp.getData();
    }

    /**
     * 创建支付成功后，把订单快照（含 username/email）缓存到 Redis 30 分钟，
     * 供支付宝异步回调时使用（替代回调里读 UserMapper）。
     */
    private void cacheOrderSnapshot(OrderSnapshotVO order) {
        try {
            redisTemplate.opsForValue().set(
                    SNAPSHOT_KEY_PREFIX + order.getOrderNo(),
                    order,
                    SNAPSHOT_TTL_MINUTES,
                    TimeUnit.MINUTES);
            log.debug("[Redis] 订单快照已缓存: orderNo={}, TTL={}min",
                    order.getOrderNo(), SNAPSHOT_TTL_MINUTES);
        } catch (Exception e) {
            log.warn("[Redis] 缓存订单快照失败（不影响主流程）: orderNo={}, err={}",
                    order.getOrderNo(), e.toString());
        }
    }

    /**
     * 从 PayOutbox 构造 OrderPaidPayload（PayOutboxRelay 调用）
     */
    public OrderPaidPayload buildPayload(PayOutbox outbox) {
        return OrderPaidPayload.builder()
                .eventId(outbox.getEventId())
                .payNo(outbox.getPayNo())
                .orderNo(outbox.getOrderNo())
                .userId(outbox.getUserId())
                .username(outbox.getUsername())
                .email(outbox.getEmail())
                .payAmount(outbox.getPayAmount())
                .paidAt(outbox.getPaidAt())
                .tradeNo(outbox.getTradeNo())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Override
    public Long findOrderIdByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        return payment == null ? null : payment.getOrderId();
    }


}
