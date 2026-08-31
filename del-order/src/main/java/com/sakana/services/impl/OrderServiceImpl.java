package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.dao.entity.Order;
import com.sakana.dao.entity.OrderItem;
import com.sakana.dao.entity.OrderOutbox;
import com.sakana.dao.mapper.OrderItemMapper;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.dao.mapper.OrderOutboxMapper;
import com.sakana.dto.request.OrderCreateReq;
import com.sakana.dto.request.OrderItemReq;
import com.sakana.dto.request.admin.AdminOrderListQuery;
import com.sakana.dto.request.admin.AdminOrderStatusReq;
import com.sakana.enums.OrderStatus;
import com.sakana.exceptions.BizException;
import com.sakana.services.OrderService;
import com.sakana.web.vo.AdminOrderPageResp;
import com.sakana.web.vo.AdminOrderVO;
import com.sakana.web.vo.OrderItemVO;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单服务实现（C 端 + B 端 + 内部）
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderItemMapper orderItemMapper;
    private final OrderOutboxMapper orderOutboxMapper;

    public OrderServiceImpl(OrderItemMapper orderItemMapper, OrderOutboxMapper orderOutboxMapper) {
        this.orderItemMapper = orderItemMapper;
        this.orderOutboxMapper = orderOutboxMapper;
    }

    // ==================== C 端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, OrderCreateReq req) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>(req.getItems().size());
        for (OrderItemReq itemReq : req.getItems()) {
            BigDecimal subtotal = itemReq.getProductPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem item = new OrderItem();
            BeanUtils.copyProperties(itemReq, item);
            item.setSubtotalAmount(subtotal);
            items.add(item);
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(OrderErrorCode.ORDER_AMOUNT_INVALID);
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setReceiverAddress(req.getReceiverAddress());
        order.setRemark(req.getRemark());
        save(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        log.info("[订单创建] userId={}, orderId={}, orderNo={}, amount={}",
                userId, order.getId(), order.getOrderNo(), totalAmount);

        // 写入 order_outbox（同一事务），由 OrderOutboxRelay 投递到 MQ
        // 注意：这里需要用户信息，但 order 表没有存储用户邮箱
        // 后续可通过 Feign 调用 del-user 获取用户邮箱，或在 OrderCreateReq 中传入
        OrderOutbox outbox = new OrderOutbox();
        outbox.setEventId(generateEventId());
        outbox.setEventType("ORDER_CREATED");
        outbox.setOrderNo(order.getOrderNo());
        outbox.setUserId(userId);
        outbox.setUsername(null); // TODO: 通过 Feign 获取或从请求中传入
        outbox.setEmail(null);    // TODO: 通过 Feign 获取或从请求中传入
        outbox.setTotalAmount(totalAmount);
        outbox.setStatus(0); // NEW
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        orderOutboxMapper.insert(outbox);

        log.info("[订单创建] outbox 已写入: eventId={}, orderNo={}", outbox.getEventId(), order.getOrderNo());

        return toVO(order, items);
    }

    @Override
    public OrderPageResp getMyOrders(Long userId, Integer status, Integer page, Integer size) {
        Page<Order> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status);
        Page<Order> pageResult = page(pageParam, wrapper);

        List<Long> orderIds = pageResult.getRecords().stream()
                .map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemMap = loadItemsByOrderIds(orderIds);

        OrderPageResp resp = new OrderPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage((long) pageResult.getCurrent());
        resp.setSize((long) pageResult.getSize());
        resp.setRecords(pageResult.getRecords().stream()
                .map(o -> toVO(o, itemMap.getOrDefault(o.getId(), Collections.emptyList())))
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_OWN);
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return toVO(order, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_OWN);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, "只有待支付状态可以取消");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        updateById(order);
        log.info("[用户取消订单] userId={}, orderId={}, orderNo={}", userId, orderId, order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_OWN);
        }
        if (order.getStatus() != OrderStatus.SHIPPING.getCode()) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, "只有配送中状态可以确认收货");
        }
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        updateById(order);
        log.info("[用户确认收货] userId={}, orderId={}, orderNo={}", userId, orderId, order.getOrderNo());
    }

    // ==================== B 端（管理后台） ====================

    @Override
    public AdminOrderPageResp adminGetPage(AdminOrderListQuery query) {
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 10 : query.getSize();

        Page<Order> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getIsDeleted, 0)
               .orderByDesc(Order::getCreateTime);
        if (query.getStatus() != null) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        

        Page<Order> pageResult = page(pageParam, wrapper);

        List<Long> orderIds = pageResult.getRecords().stream()
                .map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemMap = loadItemsByOrderIds(orderIds);

        AdminOrderPageResp resp = new AdminOrderPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage((long) pageResult.getCurrent());
        resp.setSize((long) pageResult.getSize());
        resp.setRecords(pageResult.getRecords().stream()
                .map(o -> toAdminVO(o, null, itemMap.getOrDefault(o.getId(), Collections.emptyList())))
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    public AdminOrderVO adminGetDetail(Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return toAdminVO(order, null, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminChangeStatus(Long orderId, AdminOrderStatusReq req, Long adminId, String adminName) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        Integer fromStatus = order.getStatus();
        Integer toStatus = req.getStatus();

        // 状态机校验：基于合法流转表（与单体一致）
        if (!isValidAdminTransition(fromStatus, toStatus)) {
            log.warn("[管理员改状态-非法跳转] adminId={}({}), orderId={}, orderNo={}, from={}, to={}, reason={}",
                    adminId, adminName, orderId, order.getOrderNo(), fromStatus, toStatus, req.getRemark());
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID,
                    "非法状态流转: " + fromStatus + " → " + toStatus);
        }

        if (fromStatus.equals(toStatus)) {
            log.info("[管理员改状态-无操作] adminId={}, orderId={}, status={}", adminId, orderId, fromStatus);
            return;
        }

        order.setStatus(toStatus);
        switch (toStatus) {
            case 2 -> order.setPayTime(LocalDateTime.now());
            case 3 -> order.setShipTime(LocalDateTime.now());
            case 4 -> order.setCompleteTime(LocalDateTime.now());
            case 5 -> order.setCancelTime(LocalDateTime.now());
            default -> { /* no-op */ }
        }
        updateById(order);

        log.info("[管理员改状态-成功] adminId={}({}), orderId={}, orderNo={}, from={} → to={}, reason={}",
                adminId, adminName, orderId, order.getOrderNo(), fromStatus, toStatus, req.getRemark());
    }

    /**
     * 合法状态流转表（管理员操作）
     */
    private static final Map<Integer, Set<Integer>> ADMIN_TRANSITIONS = Map.of(
            1, Set.of(2, 5),
            2, Set.of(3, 5),
            3, Set.of(4, 5)
    );

    private boolean isValidAdminTransition(Integer from, Integer to) {
        if (from == null || to == null) return false;
        Set<Integer> allowed = ADMIN_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    // ==================== 内部 / 兼容 ====================

    @Override
    @Deprecated
    public void markPaid(Long orderId, String paymentNo) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            log.warn("[markPaid] 订单不存在 orderId={}", orderId);
            return;
        }
        if (order.getStatus() != null && order.getStatus() == OrderStatus.PAID.getCode()) {
            log.info("[markPaid] 订单已是 PAID，跳过 orderId={}", orderId);
            return;
        }
        order.setStatus(OrderStatus.PAID.getCode());
        order.setPayTime(LocalDateTime.now());
        updateById(order);
        log.info("[markPaid] 订单状态已更新: orderId={} -> PAID, paymentNo={}", orderId, paymentNo);
    }

    // ==================== 私有 ====================

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + timestamp + String.format("%04d", random);
    }

    private String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Map<Long, List<OrderItem>> loadItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        return allItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusDesc(OrderStatus.fromCode(order.getStatus()).getDesc());

        List<OrderItemVO> itemVOs = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemVO itemVO = new OrderItemVO();
            BeanUtils.copyProperties(item, itemVO);
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        return vo;
    }

    private AdminOrderVO toAdminVO(Order order, String username, List<OrderItem> items) {
        AdminOrderVO vo = new AdminOrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusDesc(OrderStatus.fromCode(order.getStatus()).getDesc());
        // username 留空，由 del-user 内部接口 /internal/users/{id}/contact 补全（解耦）
        vo.setUsername(username);

        List<OrderItemVO> itemVOs = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemVO itemVO = new OrderItemVO();
            BeanUtils.copyProperties(item, itemVO);
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        return vo;
    }
}
