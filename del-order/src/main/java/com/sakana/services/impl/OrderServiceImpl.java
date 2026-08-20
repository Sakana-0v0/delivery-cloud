package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.dao.entity.Order;
import com.sakana.dao.entity.OrderItem;
import com.sakana.dao.mapper.OrderItemMapper;
import com.sakana.dao.mapper.OrderMapper;
import com.sakana.dto.request.OrderCreateReq;
import com.sakana.dto.request.OrderItemReq;
import com.sakana.enums.OrderStatus;
import com.sakana.exceptions.BizException;
import com.sakana.services.OrderService;
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
import java.util.stream.Collectors;

/**
 * 订单服务实现（骨架版本）
 *
 * <p>TODO（后续阶段）：
 * <ul>
 *   <li>集成 del-product 校验库存 / 扣减库存</li>
 *   <li>集成 del-payment 完成支付流程</li>
 *   <li>订单超时自动取消（延时队列）</li>
 *   <li>集成 del-cart（购物车结算）</li>
 *   <li>分布式事务（Seata）保证订单一致性</li>
 * </ul>
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    /** 支付超时：30 分钟 */
    private static final long PAYMENT_TIMEOUT_MINUTES = 30L;

    private final OrderItemMapper orderItemMapper;

    public OrderServiceImpl(OrderItemMapper orderItemMapper) {
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, OrderCreateReq req) {
        // 1. 计算总金额并构造订单项
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

        // 2. 创建订单主表
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setReceiverAddress(req.getReceiverAddress());
        order.setRemark(req.getRemark());
        order.setPaymentDeadline(LocalDateTime.now().plusMinutes(PAYMENT_TIMEOUT_MINUTES));
        save(order);

        // 3. 批量保存订单项
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
        }
        for (OrderItem item : items) {
            orderItemMapper.insert(item);
        }

        log.info("[订单创建] userId={}, orderId={}, orderNo={}, amount={}",
                userId, order.getId(), order.getOrderNo(), totalAmount);

        // TODO: 扣减库存（集成 del-product 后实现）

        return toVO(order, items);
    }

    @Override
    public OrderPageResp getMyOrders(Long userId, Integer status, Integer page, Integer size) {
        Page<Order> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
               .eq(status != null, Order::getStatus, status);

        Page<Order> pageResult = page(pageParam, wrapper);
        // 批量加载订单项
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
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId));
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
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        updateById(order);

        // TODO: 回滚库存（集成 del-product 后实现）

        log.info("[订单取消] userId={}, orderId={}", userId, orderId);
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
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        updateById(order);

        log.info("[确认收货] userId={}, orderId={}", userId, orderId);
    }

    // ==================== 私有方法 ====================

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + timestamp + String.format("%04d", random);
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
}

