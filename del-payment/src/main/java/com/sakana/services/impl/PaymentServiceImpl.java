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
import com.sakana.services.FreeOrderGrabService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String SNAPSHOT_KEY_PREFIX = "pay:order-snapshot:";
    private static final long SNAPSHOT_TTL_MINUTES = 30;

    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;
    private final PaymentMapper paymentMapper;
    private final PayOutboxMapper outboxMapper;
    private final OrderClient orderClient;
    private final UserClient userClient;
    private final RedisTemplate<String, OrderSnapshotVO> redisTemplate;
    private final FreeOrderGrabService freeOrderGrabService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    // ====================================================================
    // 1. createPayment（含免单逻辑）
    // ====================================================================
    // @Override  // 2-param overload, not in interface
    public PaymentVO createPayment(Long userId, Long orderId) {
        return createPayment(userId, orderId, null);
    }

    @Override
    public PaymentVO createPayment(Long userId, Long orderId, String freeOrderCode) {
        log.info("[创建支付] ========== START ==========");
        log.info("[创建支付] userId={}, orderId={}, freeOrderCode={}", userId, orderId, freeOrderCode);

        log.info("========== [创建支付] 支付宝配置传输明细 ==========");
        log.info("[配置传输] gateway     = {}", alipayProperties.getGateway());
        log.info("[配置传输] appId       = {}", alipayProperties.getAppId());
        log.info("[配置传输] notifyUrl   = {}", alipayProperties.getNotifyUrl());
        log.info("[配置传输] returnUrl   = {}", alipayProperties.getReturnUrl());
        log.info("====================================================");

        // 1. 通过 Feign 取订单快照
        OrderSnapshotVO order = fetchOrderSnapshot(orderId);
        log.info("[创建支付] 订单快照: orderNo={}, status={}, payAmount={}, userId={}",
                order.getOrderNo(), order.getStatus(), order.getPayAmount(), order.getUserId());

        // 2. 鉴权 + 可付性校验
        if (!order.getUserId().equals(userId)) {
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL, "无权为他人创建支付");
        }
        if (!Boolean.TRUE.equals(order.getCanPay())) {
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL, "当前订单状态不允许支付");
        }

        // 3. 幂等：检查是否已有成功支付记录
        Payment existing = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getStatus, PayStatus.SUCCESS.code()));
        if (existing != null) {
            log.warn("[创建支付] 订单已支付，跳过: orderId={}", orderId);
            throw new BizException(PayErrorCode.PAY_ALREADY_PAID);
        }

        // ========== ★ BUG-007 智能免单支付 ★ ==========
        if (freeOrderCode != null && !freeOrderCode.isBlank()) {
            // 1. 查免单额度
            BigDecimal freeAmount = freeOrderGrabService.getFreeOrderMaxAmount(freeOrderCode);
            if (freeAmount == null) {
                throw new BizException(PayErrorCode.FREE_ORDER_CODE_INVALID, "免单码无效");
            }
            // 2. 计算实付金额
            BigDecimal orderAmount = order.getPayAmount();
            BigDecimal finalAmount = freeOrderGrabService.calculateFinalAmount(orderAmount, freeAmount);
            log.info("[创建支付] 免单计算: orderAmount={}, freeAmount={}, finalAmount={}",
                    orderAmount, freeAmount, finalAmount);
            // 3. 智能分派：0 元不走支付宝，>0 元部分免单走支付宝
            if (finalAmount.signum() == 0) {
                return doFreeOrderPayment(userId, orderId, order, freeOrderCode, finalAmount);
            } else {
                return doPartialFreeOrderPayment(userId, orderId, order, freeOrderCode, finalAmount);
            }
        }

        // ========== 正常支付宝支付（无免单）==========
        return doAlipayPayment(userId, orderId, order, order.getPayAmount());
    }

    /**
     * 免单支付：0元直接标成功 + 写 PayOutbox
     */
    @Transactional(rollbackFor = Exception.class)
    protected PaymentVO doFreeOrderPayment(Long userId, Long orderId,
                                           OrderSnapshotVO order, String freeOrderCode, BigDecimal finalAmount) {
        log.info("[免单支付] 开始 freeOrderCode={}, orderNo={}", freeOrderCode, order.getOrderNo());

        // 原子化使用免单码（校验本人+未使用）
        boolean applied = freeOrderGrabService.applyFreeOrder(freeOrderCode, userId, orderId);
        if (!applied) {
            throw new BizException(PayErrorCode.FREE_ORDER_CODE_INVALID);
        }

        String payNo = "FREE" + System.currentTimeMillis();

        // 写 Payment 记录（0元成功）
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setPayNo(payNo);
        payment.setChannel("FREE_ORDER");
        payment.setAmount(finalAmount);
        payment.setStatus(PayStatus.SUCCESS.code());
        payment.setPaidAt(LocalDateTime.now());
        payment.setTradeNo(payNo);
        paymentMapper.insert(payment);
        log.info("[免单支付] Payment 已写入: payNo={}, amount=0", payNo);

        // 写 PayOutbox（触发 MQ，del-order 更新订单状态）
        writeOutbox(payment, order, payNo);

        PaymentVO vo = new PaymentVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setFreeOrder(true);
        vo.setFreeOrderCode(freeOrderCode);
        vo.setPaidAmount(BigDecimal.ZERO);
        vo.setStatus("SUCCESS");
        log.info("[免单支付] ========== SUCCESS ==========");
        return vo;
    }

    /**
     * 正常支付宝支付
     */
    private PaymentVO doAlipayPayment(Long userId, Long orderId, OrderSnapshotVO order, BigDecimal amount) {
        String payNo = "PAY" + System.currentTimeMillis();

        // 写 Payment 记录（待支付）
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setPayNo(payNo);
        payment.setChannel("alipay");
        payment.setAmount(amount);
        payment.setStatus(PayStatus.PENDING.code());
        paymentMapper.insert(payment);

        // 缓存快照
        cacheOrderSnapshot(order);

        // 调支付宝
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(alipayProperties.getReturnUrl());
        request.setNotifyUrl(alipayProperties.getNotifyUrl());

        Map<String, Object> bizMap = new LinkedHashMap<>();
        bizMap.put("out_trade_no", payNo);
        bizMap.put("total_amount", amount.toString());
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
                vo.setFreeOrder(false);
                vo.setPaidAmount(order.getPayAmount());
                vo.setStatus("PENDING");
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
    // 2. handleNotify
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params) {
        log.info("[支付回调] 收到回调, params={}", params);

        String payNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");
        String tradeNo = params.get("trade_no");

        // 1. 验签
        try {
            boolean signCheck = AlipaySignature.rsaCheckV1(
                    params, alipayProperties.getPublicKey(), "UTF-8", "RSA2");
            if (!signCheck) {
                log.warn("[支付回调] 验签失败: payNo={}", payNo);
                return "fail";
            }
        } catch (Exception e) {
            log.error("[支付回调] 验签异常: payNo={}", payNo, e);
            return "fail";
        }

        // 2. 查询支付记录
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
        if (payment == null) {
            log.warn("[支付回调] 支付记录不存在: payNo={}", payNo);
            return "fail";
        }

        // 3. 幂等
        if (payment.getStatus() != null && payment.getStatus() == PayStatus.SUCCESS.code()) {
            log.info("[支付回调] 幂等跳过: payNo={}", payNo);
            return "success";
        }

        // 4. 交易状态
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

        // 6. 更新 Payment
        payment.setStatus(PayStatus.SUCCESS.code());
        payment.setPaidAt(LocalDateTime.now());
        payment.setTradeNo(tradeNo);
        payment.setRawNotify(params.toString());
        paymentMapper.updateById(payment);

        // 7. 写 outbox
        OrderSnapshotVO snapshot = getOrderSnapshot(payment.getOrderNo());
        if (snapshot == null) {
            snapshot = new OrderSnapshotVO();
            snapshot.setOrderNo(payment.getOrderNo());
            snapshot.setUserId(extractUserIdFromPayment(payment));
            try {
                if (snapshot.getUserId() != null) {
                    Map<String, Object> contact = userClient.getUserContact(snapshot.getUserId()).getData();
                    if (contact != null) {
                        snapshot.setUsername((String) contact.get("username"));
                        snapshot.setEmail((String) contact.get("email"));
                    }
                }
            } catch (Exception ex) {
                log.warn("[支付回调] UserClient 兜底失败: {}", ex.toString());
            }
        }
        writeOutboxDirect(payment, snapshot, tradeNo);

        log.info("[支付回调] ========== SUCCESS ==========");
        return "success";
    }

    // ====================================================================
    // 3. queryStatus
    // ====================================================================
    @Override
    public PaymentStatusVO queryStatus(Long userId, String orderNo) {
        log.info("[查询状态] userId={}, orderNo={}", userId, orderNo);

        Payment paymentForCheck = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (paymentForCheck == null) {
            throw new BizException(PayErrorCode.PAY_NOT_FOUND, "订单不存在或尚未发起支付");
        }

        OrderSnapshotVO order = null;
        try {
            order = fetchOrderSnapshot(paymentForCheck.getOrderId());
        } catch (Exception e) {
            log.warn("[查询状态] 取订单快照失败: {}", e.toString());
        }

        if (order != null && !order.getUserId().equals(userId)) {
            throw new BizException(PayErrorCode.PAY_CREATE_FAIL, "无权访问此订单");
        }

        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));

        PaymentStatusVO vo = new PaymentStatusVO();
        vo.setOrderNo(orderNo);
        if (payment != null) {
            vo.setPayNo(payment.getPayNo());
            vo.setStatus(payment.getStatus());
            vo.setPaidAt(payment.getPaidAt());
        }

        if (payment != null && payment.getStatus() != null
                && payment.getStatus() == PayStatus.SUCCESS.code()) {
            // 本地已成功
        } else if (payment != null && "alipay".equals(payment.getChannel())) {
            // 支付宝：轮询查询
            try {
                AlipayTradeQueryRequest queryReq = new AlipayTradeQueryRequest();
                queryReq.setBizContent("{\"out_trade_no\":\"" + payment.getPayNo() + "\"}");
                AlipayTradeQueryResponse queryResp = alipayClient.execute(queryReq);
                if (queryResp.isSuccess() && "TRADE_SUCCESS".equals(queryResp.getTradeStatus())) {
                    payment.setStatus(PayStatus.SUCCESS.code());
                    payment.setPaidAt(LocalDateTime.now());
                    payment.setTradeNo(queryResp.getTradeNo());
                    paymentMapper.updateById(payment);
                    vo.setStatus(PayStatus.SUCCESS.code());
                    log.info("[查询状态] 支付宝轮询成功: payNo={}", payment.getPayNo());
                }
            } catch (Exception e) {
                log.warn("[查询状态] 支付宝轮询失败: {}", e.toString());
            }
        } else if (order != null && Boolean.TRUE.equals(order.getCanPay())
                && (payment == null || payment.getStatus() != PayStatus.SUCCESS.code())) {
            log.info("[查询状态] 本地无支付记录但订单可付，标失败: orderNo={}", orderNo);
        }

        log.info("[查询状态] 返回: orderNo={}, payStatus={}", orderNo, vo.getStatus());
        return vo;
    }

    // ====================================================================
    // 4. 内部 API
    // ====================================================================
    @Override
    public PaymentInternalVO queryByOrderNo(String orderNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        return PaymentInternalVO.from(payment);
    }

    @Override
    public boolean isOrderPaid(String orderNo) {
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

    @Override
    public Long findOrderIdByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        return payment == null ? null : payment.getOrderId();
    }

    // ====================================================================
    // 私有辅助
    // ====================================================================

    private void writeOutbox(Payment payment, OrderSnapshotVO order, String tradeNo) {
        String eventId = UUID.randomUUID().toString();
        PayOutbox outbox = new PayOutbox();
        outbox.setEventId(eventId);
        outbox.setPayNo(payment.getPayNo());
        outbox.setOrderNo(payment.getOrderNo());
        outbox.setUserId(order.getUserId());
        outbox.setUsername(order.getUsername());
        outbox.setEmail(order.getEmail());
        outbox.setPayAmount(payment.getAmount());
        outbox.setPaidAt(payment.getPaidAt());
        outbox.setTradeNo(tradeNo);
        outbox.setStatus(PayOutboxStatus.NEW.code());
        outbox.setRetryCount(0);
        outboxMapper.insert(outbox);
        log.info("[免单支付] outbox 已写入: eventId={}, orderNo={}", eventId, payment.getOrderNo());
    }

    private void writeOutboxDirect(Payment payment, OrderSnapshotVO snapshot, String tradeNo) {
        String eventId = UUID.randomUUID().toString();
        PayOutbox outbox = new PayOutbox();
        outbox.setEventId(eventId);
        outbox.setPayNo(payment.getPayNo());
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
    }

    private void cacheOrderSnapshot(OrderSnapshotVO order) {
        try {
            redisTemplate.opsForValue().set(
                    SNAPSHOT_KEY_PREFIX + order.getOrderNo(),
                    order,
                    SNAPSHOT_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[Redis] 缓存订单快照失败: orderNo={}, err={}",
                    order.getOrderNo(), e.toString());
        }
    }

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

    private Long extractUserIdFromPayment(Payment payment) {
        OrderSnapshotVO cached = getOrderSnapshot(payment.getOrderNo());
        return cached != null ? cached.getUserId() : null;
    }

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


    /**
     * BUG-007：部分免单（订单 > 免单额度）+ 走支付宝
     */
    @Transactional(rollbackFor = Exception.class)
    protected PaymentVO doPartialFreeOrderPayment(Long userId, Long orderId,
                                                  OrderSnapshotVO order, String freeOrderCode,
                                                  BigDecimal finalAmount) {
        log.info("[部分免单] 开始 code={}, orderAmount={}, finalAmount={}",
                freeOrderCode, order.getPayAmount(), finalAmount);

        // 1. 标记免单码为 USED
        boolean applied = freeOrderGrabService.applyFreeOrder(freeOrderCode, userId, orderId);
        if (!applied) {
            throw new BizException(PayErrorCode.FREE_ORDER_CODE_INVALID);
        }

        // 2. 走支付宝（用 finalAmount）
        return doAlipayPayment(userId, orderId, order, finalAmount);
    }
}
