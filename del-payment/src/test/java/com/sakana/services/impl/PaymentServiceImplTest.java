package com.sakana.services.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.configs.alipay.AlipayProperties;
import com.sakana.dao.entity.PayOutbox;
import com.sakana.dao.entity.Payment;
import com.sakana.dao.mapper.PayOutboxMapper;
import com.sakana.dao.mapper.PaymentMapper;
import com.sakana.enums.PayErrorCode;
import com.sakana.enums.PayOutboxStatus;
import com.sakana.enums.PayStatus;
import com.sakana.exceptions.BizException;
import com.sakana.feign.OrderClient;
import com.sakana.feign.UserClient;
import com.sakana.feign.vo.OrderSnapshotVO;
import com.sakana.web.vo.PaymentInternalVO;
import com.sakana.web.vo.PaymentStatusVO;
import com.sakana.web.vo.PaymentVO;
import com.sakana.web.vo.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alipay.api.AlipayApiException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PaymentServiceImpl 单元测试
 *
 * <p>覆盖：createPayment / handleNotify / queryStatus / queryByOrderNo / isOrderPaid / getOrderSnapshot
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private AlipayClient alipayClient;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PayOutboxMapper outboxMapper;
    @Mock private OrderClient orderClient;
    @Mock private UserClient userClient;
    @Mock private RedisTemplate<String, OrderSnapshotVO> redisTemplate;
    @Mock private ValueOperations<String, OrderSnapshotVO> valueOperations;

    private AlipayProperties alipayProperties;
    private PaymentServiceImpl service;
    private MockedStatic<AlipaySignature> signatureMock;

    @BeforeEach
    void setUp() throws com.alipay.api.AlipayApiException {
        alipayProperties = new AlipayProperties();
        alipayProperties.setAppId("2026000000000000");
        alipayProperties.setPrivateKey("private-key-1234567890");
        alipayProperties.setPublicKey("public-key-1234567890");
        alipayProperties.setGateway("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        alipayProperties.setNotifyUrl("https://example.com/notify");
        alipayProperties.setReturnUrl("https://example.com/return");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new PaymentServiceImpl(
                alipayClient, alipayProperties, paymentMapper, outboxMapper,
                orderClient, userClient, redisTemplate);

        // 默认静态 mock：验签失败（各测试按需 override）
        signatureMock = Mockito.mockStatic(AlipaySignature.class);
    }

    @AfterEach
    void tearDown() throws com.alipay.api.AlipayApiException {
        signatureMock.close();
    }

    private OrderSnapshotVO snapshot(Long orderId, String orderNo, Long userId, boolean canPay) {
        OrderSnapshotVO s = new OrderSnapshotVO();
        s.setId(orderId);
        s.setOrderNo(orderNo);
        s.setUserId(userId);
        s.setPayAmount(new BigDecimal("100.00"));
        s.setStatus(1);  // PENDING_PAYMENT
        s.setCanPay(canPay);
        s.setUsername("alice");
        s.setEmail("alice@test.com");
        return s;
    }

    private Payment existingPayment(String payNo, Integer status, BigDecimal amount) {
        Payment p = new Payment();
        p.setId(1L);
        p.setOrderId(100L);
        p.setOrderNo("ORD20260101");
        p.setPayNo(payNo);
        p.setChannel("alipay");
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }

    // ==================== createPayment ====================

    @Test
    void createPayment_happyPath_returnsPayForm() throws com.alipay.api.AlipayApiException {
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 1L, true);
        when(orderClient.getOrderForPayment(100L)).thenReturn(R.ok(s));
        AlipayTradePagePayResponse resp = new AlipayTradePagePayResponse();
        resp.setCode("10000");
        resp.setMsg("Success");
        resp.setBody("<form>pay-form-html</form>");
        resp.setSubCode("");
        when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(resp);

        PaymentVO vo = service.createPayment(1L, 100L);
        assertNotNull(vo);
        assertEquals("ORD20260101", vo.getOrderNo());
        assertEquals("<form>pay-form-html</form>", vo.getPayForm());

        // 验证 Payment 记录已落库
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(captor.capture());
        assertEquals(PayStatus.PENDING.code(), captor.getValue().getStatus());
        assertEquals("alipay", captor.getValue().getChannel());

        // 验证快照已缓存
        verify(valueOperations).set(eq("pay:order-snapshot:ORD20260101"), eq(s), anyLong(), any());
    }

    @Test
    void createPayment_otherUser_throws() throws com.alipay.api.AlipayApiException {
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 999L, true);
        when(orderClient.getOrderForPayment(100L)).thenReturn(R.ok(s));

        BizException ex = assertThrows(BizException.class,
                () -> service.createPayment(1L, 100L));
        assertEquals(PayErrorCode.PAY_CREATE_FAIL.getCode(), ex.getCode());
    }

    @Test
    void createPayment_canPayFalse_throwsAlreadyPaid() throws com.alipay.api.AlipayApiException {
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 1L, false);  // 已支付
        when(orderClient.getOrderForPayment(100L)).thenReturn(R.ok(s));

        BizException ex = assertThrows(BizException.class,
                () -> service.createPayment(1L, 100L));
        assertEquals(PayErrorCode.PAY_ALREADY_PAID.getCode(), ex.getCode());
    }

    @Test
    void createPayment_orderServiceUnavailable_throws() throws com.alipay.api.AlipayApiException {
        when(orderClient.getOrderForPayment(100L)).thenReturn(R.fail(404, "订单不存在"));

        BizException ex = assertThrows(BizException.class,
                () -> service.createPayment(1L, 100L));
        assertEquals(PayErrorCode.ORDER_SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void createPayment_alipayResponseFail_throws() throws com.alipay.api.AlipayApiException {
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 1L, true);
        when(orderClient.getOrderForPayment(100L)).thenReturn(R.ok(s));
        AlipayTradePagePayResponse resp = new AlipayTradePagePayResponse();
        resp.setCode("40004");
        resp.setMsg("Business Failed");
        resp.setSubCode("ACQ.SYSTEM_ERROR");
        when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(resp);

        assertThrows(BizException.class, () -> service.createPayment(1L, 100L));
    }

    // ==================== handleNotify ====================

    private Map<String, String> validNotifyParams() {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "pay123");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "100.00");
        params.put("trade_no", "20260101234567890123");
        return params;
    }

    @Test
    void handleNotify_signatureInvalid_returnsFail() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(false);

        assertEquals("fail", service.handleNotify(validNotifyParams()));
        verify(paymentMapper, never()).updateById(any(Payment.class));
        verify(outboxMapper, never()).insert(any(PayOutbox.class));
    }

    @Test
    void handleNotify_paymentNotFound_returnsFail() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertEquals("fail", service.handleNotify(validNotifyParams()));
        verify(outboxMapper, never()).insert(any(PayOutbox.class));
    }

    @Test
    void handleNotify_idempotent_alreadySuccess_returnsSuccess() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        Payment p = existingPayment("pay123", PayStatus.SUCCESS.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);

        assertEquals("success", service.handleNotify(validNotifyParams()));
        // 已成功的支付不应再 update / 写 outbox
        verify(paymentMapper, never()).updateById(any(Payment.class));
        verify(outboxMapper, never()).insert(any(PayOutbox.class));
    }

    @Test
    void handleNotify_tradeStatusNotSuccess_returnsFail() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        Payment p = existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);

        Map<String, String> params = validNotifyParams();
        params.put("trade_status", "WAIT_BUYER_PAY");
        assertEquals("fail", service.handleNotify(params));
        verify(paymentMapper, never()).updateById(any(Payment.class));
    }

    @Test
    void handleNotify_amountMismatch_returnsFail() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        Payment p = existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);

        Map<String, String> params = validNotifyParams();
        params.put("total_amount", "99.99");  // 金额不匹配
        assertEquals("fail", service.handleNotify(params));
        verify(paymentMapper, never()).updateById(any(Payment.class));
    }

    @Test
    void handleNotify_happyPath_updatesPaymentAndInsertsOutbox() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        Payment p = existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);

        // 缓存里有订单快照（含 username/email）
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 1L, true);
        when(valueOperations.get("pay:order-snapshot:ORD20260101")).thenReturn(s);

        assertEquals("success", service.handleNotify(validNotifyParams()));

        // 验证 Payment 已更新
        ArgumentCaptor<Payment> payCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).updateById(payCaptor.capture());
        assertEquals(PayStatus.SUCCESS.code(), payCaptor.getValue().getStatus());
        assertNotNull(payCaptor.getValue().getPaidAt());
        assertEquals("20260101234567890123", payCaptor.getValue().getTradeNo());

        // 验证 Outbox 已写入
        ArgumentCaptor<PayOutbox> outboxCaptor = ArgumentCaptor.forClass(PayOutbox.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals(PayOutboxStatus.NEW.code(), outboxCaptor.getValue().getStatus());
        assertEquals(0, outboxCaptor.getValue().getRetryCount());
        assertEquals("pay123", outboxCaptor.getValue().getPayNo());
        assertEquals("ORD20260101", outboxCaptor.getValue().getOrderNo());
        assertEquals("alice", outboxCaptor.getValue().getUsername());
        assertEquals("alice@test.com", outboxCaptor.getValue().getEmail());
    }

    @Test
    void handleNotify_snapshotExpired_fallsBackToUserClient() throws AlipayApiException {
        signatureMock.when(() -> AlipaySignature.rsaCheckV1(
                anyMap(), anyString(), anyString(), anyString())).thenReturn(true);
        Payment p = existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);

        // Redis 快照已过期
        when(valueOperations.get("pay:order-snapshot:ORD20260101")).thenReturn(null);
        // 但 Redis 还有一份"含 userId"的过期快照（extractUserIdFromPayment 用）
        OrderSnapshotVO staleSnapshot = new OrderSnapshotVO();
        staleSnapshot.setUserId(1L);
        // 第一次 get 返回 null（快照真过期）；第二次 get 也返回 null（兜底也失败）
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userClient.getUserContact(1L)).thenReturn(R.ok(Map.of("username", "alice", "email", "alice@test.com")));

        // 即使用户服务也走不通，也不影响支付主流程
        assertEquals("success", service.handleNotify(validNotifyParams()));
    }

    // ==================== queryStatus ====================

    @Test
    void queryStatus_happyPath_returnsStatus() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00")));
        when(orderClient.getOrderForPayment(100L))
                .thenReturn(R.ok(snapshot(100L, "ORD20260101", 1L, true)));

        PaymentStatusVO vo = service.queryStatus(1L, "ORD20260101");
        assertEquals("ORD20260101", vo.getOrderNo());
        assertEquals("pay123", vo.getPayNo());
        assertEquals(PayStatus.PENDING.code(), vo.getStatus());
    }

    @Test
    void queryStatus_paymentNotFound_throws() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.queryStatus(1L, "GHOST"));
        assertEquals(PayErrorCode.PAY_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void queryStatus_orderBelongsToOtherUser_throws() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00")));
        when(orderClient.getOrderForPayment(100L))
                .thenReturn(R.ok(snapshot(100L, "ORD20260101", 999L, true)));

        BizException ex = assertThrows(BizException.class,
                () -> service.queryStatus(1L, "ORD20260101"));
        assertEquals(PayErrorCode.PAY_CREATE_FAIL.getCode(), ex.getCode());
    }

    @Test
    void queryStatus_alipaySuccess_triggersLocalUpdate() throws com.alipay.api.AlipayApiException {
        Payment p = existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
        when(orderClient.getOrderForPayment(100L))
                .thenReturn(R.ok(snapshot(100L, "ORD20260101", 1L, true)));

        AlipayTradeQueryResponse queryResp = new AlipayTradeQueryResponse();
        queryResp.setCode("10000");
        queryResp.setMsg("Success");
        queryResp.setTradeStatus("TRADE_SUCCESS");
        queryResp.setTradeNo("alipay-trade-123");
        queryResp.setBuyerUserId("buyer-2088");
        queryResp.setBuyerLogonId("buyer***@sandbox.com");
        when(alipayClient.execute(any(com.alipay.api.AlipayRequest.class))).thenReturn(queryResp);

        PaymentStatusVO vo = service.queryStatus(1L, "ORD20260101");
        assertEquals(PayStatus.SUCCESS.code(), vo.getStatus());
        assertEquals("alipay-trade-123", vo.getTradeNo());
        assertEquals("buyer-2088", vo.getBuyerUserId());

        // 本地 Payment 状态被更新
        verify(paymentMapper).updateById(any(Payment.class));
    }

    @Test
    void queryStatus_alipayFailure_doesNotThrow() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00")));
        when(orderClient.getOrderForPayment(100L))
                .thenReturn(R.ok(snapshot(100L, "ORD20260101", 1L, true)));
        // 支付宝 API 抛异常
        doThrow(new RuntimeException("network error")).when(alipayClient).execute(any(com.alipay.api.AlipayRequest.class));

        // 不应抛异常，返回本地数据
        PaymentStatusVO vo = assertDoesNotThrow(() -> service.queryStatus(1L, "ORD20260101"));
        assertEquals(PayStatus.PENDING.code(), vo.getStatus());
    }

    // ==================== queryByOrderNo ====================

    @Test
    void queryByOrderNo_found_returnsVO() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.SUCCESS.code(), new BigDecimal("100.00")));

        PaymentInternalVO vo = service.queryByOrderNo("ORD20260101");
        assertNotNull(vo);
        assertEquals("ORD20260101", vo.getOrderNo());
        assertEquals("pay123", vo.getPayNo());
        assertEquals(PayStatus.SUCCESS.code(), vo.getStatus());
    }

    @Test
    void queryByOrderNo_notFound_returnsNull() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.queryByOrderNo("GHOST"));
    }

    // ==================== isOrderPaid ====================

    @Test
    void isOrderPaid_paid_returnsTrue() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.SUCCESS.code(), new BigDecimal("100.00")));
        assertTrue(service.isOrderPaid("ORD20260101"));
    }

    @Test
    void isOrderPaid_pending_returnsFalse() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPayment("pay123", PayStatus.PENDING.code(), new BigDecimal("100.00")));
        assertFalse(service.isOrderPaid("ORD20260101"));
    }

    @Test
    void isOrderPaid_noRecord_returnsFalse() throws com.alipay.api.AlipayApiException {
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.isOrderPaid("GHOST"));
    }

    @Test
    void isOrderPaid_nullStatus_returnsFalse() throws com.alipay.api.AlipayApiException {
        Payment p = existingPayment("pay123", null, new BigDecimal("100.00"));
        when(paymentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
        assertFalse(service.isOrderPaid("ORD20260101"));
    }

    // ==================== getOrderSnapshot ====================

    @Test
    void getOrderSnapshot_cached_returnsFromRedis() throws com.alipay.api.AlipayApiException {
        OrderSnapshotVO s = snapshot(100L, "ORD20260101", 1L, true);
        when(valueOperations.get("pay:order-snapshot:ORD20260101")).thenReturn(s);

        assertSame(s, service.getOrderSnapshot("ORD20260101"));
    }

    @Test
    void getOrderSnapshot_notCached_returnsNull() throws com.alipay.api.AlipayApiException {
        when(valueOperations.get("pay:order-snapshot:GHOST")).thenReturn(null);
        assertNull(service.getOrderSnapshot("GHOST"));
    }

    @Test
    void getOrderSnapshot_redisDown_returnsNull() throws com.alipay.api.AlipayApiException {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertNull(service.getOrderSnapshot("ORD20260101"));
    }
}
