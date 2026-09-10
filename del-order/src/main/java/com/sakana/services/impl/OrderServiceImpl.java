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
import com.sakana.feign.ProductFeignClient;
import com.sakana.feign.UserFeignClient;
import com.sakana.feign.vo.ProductSnapshotVO;
import com.sakana.feign.vo.UserContactVO;
import com.sakana.web.vo.R;
import com.sakana.services.OrderService;
import com.sakana.web.vo.AdminOrderPageResp;
import com.sakana.web.vo.AdminOrderVO;
import com.sakana.web.vo.OrderItemVO;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.UserAddressVO;
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
    private final UserFeignClient userFeignClient;
    private final ProductFeignClient productFeignClient;

    public OrderServiceImpl(OrderItemMapper orderItemMapper,
                            OrderOutboxMapper orderOutboxMapper,
                            UserFeignClient userFeignClient,
                            ProductFeignClient productFeignClient) {
        this.orderItemMapper = orderItemMapper;
        this.orderOutboxMapper = orderOutboxMapper;
        this.userFeignClient = userFeignClient;
        this.productFeignClient = productFeignClient;
    }

    // ==================== C 端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, OrderCreateReq req) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>(req.getItems().size());
        for (OrderItemReq itemReq : req.getItems()) {
            // 自动补全商品快照（如果前端未传递 productName/productPrice/productCover）
            fillProductSnapshot(itemReq);

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

        // 如果提供了 addressId，优先通过 Feign 调用获取地址信息
        String receiverName = req.getReceiverName();
        String receiverPhone = req.getReceiverPhone();
        String receiverAddress = req.getReceiverAddress();

        if (req.getAddressId() != null) {
            var response = userFeignClient.getAddressById(req.getAddressId());
            if (response != null && response.getData() != null) {
                UserAddressVO address = response.getData();
                receiverName = address.getReceiver();
                receiverPhone = address.getPhone();
                receiverAddress = address.getFullAddress();
                log.info("[订单创建-地址获取] addressId={}, receiver={}, phone={}, address={}",
                        req.getAddressId(), receiverName, receiverPhone, receiverAddress);
            } else {
                log.warn("[订单创建-地址未获取] addressId={}, response={}", req.getAddressId(), response);
            }
        }

        // 收货人信息必须完整
        if (receiverName == null || receiverName.isEmpty()
                || receiverPhone == null || receiverPhone.isEmpty()
                || receiverAddress == null || receiverAddress.isEmpty()) {
            log.warn("[订单创建-收货人信息不全] addressId={}, name={}, phone={}, addr={}",
                    req.getAddressId(), receiverName, receiverPhone, receiverAddress);
            throw new BizException(OrderErrorCode.ORDER_AMOUNT_INVALID, "收货人信息不完整，请提供 addressId 或完整填写收货人信息");
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
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

        // Get user contact info for outbox event
        String username = "unknown";
        String email = null;
        try {
            var contactResp = userFeignClient.getUserContact(userId);
            if (contactResp != null && contactResp.getData() != null) {
                UserContactVO contact = contactResp.getData();
                username = contact.getUsername() != null ? contact.getUsername() : "unknown";
                email = contact.getEmail();
            }
        } catch (Exception e) {
            log.warn("[order-create-user-contact-fail] userId={}, error={}", userId, e.getMessage());
        }

        outbox.setUsername(username);
        outbox.setEmail(email);    // TODO: 通过 Feign 获取或从请求中传入
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
        wrapper.eq(Order::getUserId, userId).eq(Order::getIsDeleted, 0);
        if (status != null) wrapper.eq(Order::getStatus, status);
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> pageResult = page(pageParam, wrapper);

        List<Long> orderIds = pageResult.getRecords().stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemsMap = loadItemsByOrderIds(orderIds);

        List<OrderVO> records = new ArrayList<>();
        for (Order order : pageResult.getRecords()) {
            List<OrderItem> orderItems = itemsMap.getOrDefault(order.getId(), List.of());
            records.add(toVO(order, orderItems));
        }

        OrderPageResp resp = new OrderPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage(pageResult.getCurrent());
        resp.setSize(pageResult.getSize());
        resp.setRecords(records);
        return resp;
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return toVO(order, orderItems);
    }
    @Override
    public OrderVO getByOrderNo(Long userId, String orderNo) {
        Order order = getOne(
            new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId)
        );
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<OrderItem> orderItems = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        return toVO(order, orderItems);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException(OrderErrorCode.ORDER_CANNOT_CANCEL);
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        updateById(order);
        log.info("[用户取消订单] userId={}, orderId={}", userId, orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.SHIPPING.getCode()) {
            throw new BizException(OrderErrorCode.ORDER_CANNOT_CONFIRM);
        }

        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        updateById(order);
        log.info("[用户确认收货] userId={}, orderId={}", userId, orderId);
    }

    // ==================== B 端（管理后台） ====================

    @Override
    public AdminOrderPageResp adminGetPage(AdminOrderListQuery query) {
        Page<Order> pageParam = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) wrapper.eq(Order::getUserId, query.getUserId());
        if (query.getStatus() != null) wrapper.eq(Order::getStatus, query.getStatus());
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) wrapper.likeRight(Order::getOrderNo, query.getOrderNo());
        wrapper.eq(Order::getIsDeleted, 0);
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> pageResult = page(pageParam, wrapper);
        List<Long> orderIds = pageResult.getRecords().stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemsMap = loadItemsByOrderIds(orderIds);

        List<AdminOrderVO> records = new ArrayList<>();
        for (Order order : pageResult.getRecords()) {
            List<OrderItem> orderItems = itemsMap.getOrDefault(order.getId(), List.of());
            records.add(toAdminVO(order, null, orderItems));
        }

        AdminOrderPageResp resp = new AdminOrderPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage(pageResult.getCurrent());
        resp.setSize(pageResult.getSize());
        resp.setRecords(records);
        return resp;
    }

    @Override
    public AdminOrderVO adminGetDetail(Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return toAdminVO(order, null, orderItems);
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

    /**
     * 自动补全商品快照。如果前端未传递 productName/productPrice/productCover，
     * 则通过 Feign 调用 del-product 获取。这样前端只需传 productId + quantity。
     */
    private void fillProductSnapshot(OrderItemReq itemReq) {
        if (itemReq.getProductId() == null) {
            return;
        }
        boolean needFetch = itemReq.getProductName() == null
                || itemReq.getProductPrice() == null
                || itemReq.getProductCover() == null;
        if (!needFetch) {
            return;
        }
        try {
            R<ProductSnapshotVO> resp = productFeignClient.getProductSnapshot(itemReq.getProductId());
            if (resp != null && resp.getData() != null) {
                ProductSnapshotVO snap = resp.getData();
                if (itemReq.getProductName() == null) itemReq.setProductName(snap.getName());
                if (itemReq.getProductCover() == null) itemReq.setProductCover(snap.getCover());
                if (itemReq.getProductPrice() == null) itemReq.setProductPrice(snap.getRealPrice());
                log.info("[订单创建-商品快照补全] productId={}, name={}, price={}",
                        itemReq.getProductId(), snap.getName(), snap.getRealPrice());
            }
        } catch (Exception e) {
            log.warn("[订单创建-商品快照获取失败] productId={}, error={}",
                    itemReq.getProductId(), e.getMessage());
        }
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
