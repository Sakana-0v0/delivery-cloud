package com.sakana.services;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.dao.entity.Order;
import com.sakana.dto.request.OrderCreateReq;
import com.sakana.dto.request.admin.AdminOrderListQuery;
import com.sakana.dto.request.admin.AdminOrderStatusReq;
import com.sakana.web.vo.AdminOrderPageResp;
import com.sakana.web.vo.AdminOrderVO;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;

/**
 * 订单服务接口（C 端 + B 端）
 */
public interface OrderService extends IService<Order> {

    // ==================== C 端 ====================

    OrderVO createOrder(Long userId, OrderCreateReq req);

    OrderPageResp getMyOrders(Long userId, Integer status, Integer page, Integer size);

    OrderVO getOrderDetail(Long userId, Long orderId);

    /**
     * ★ BUG-018：通过业务订单号（orderNo）查询订单
     */
    OrderVO getByOrderNo(Long userId, String orderNo);

    void cancelOrder(Long userId, Long orderId);

    void confirmOrder(Long userId, Long orderId);

    // ==================== B 端（管理后台） ====================

    /**
     * 管理后台 - 订单分页（多条件）
     */
    AdminOrderPageResp adminGetPage(AdminOrderListQuery query);

    /**
     * 管理后台 - 订单详情（含用户信息、订单项）
     */
    AdminOrderVO adminGetDetail(Long orderId);

    /**
     * 管理后台 - 修改订单状态（状态机校验）
     *
     * @param adminId   操作管理员 ID
     * @param adminName 操作管理员用户名（仅记录日志）
     */
    void adminChangeStatus(Long orderId, AdminOrderStatusReq req, Long adminId, String adminName);

    // ==================== 内部接口（其他服务调用） ====================

    /**
     * 内部：支付成功后由 del-payment 调用的接口已迁移到 RabbitMQ 事件，本方法保留以兼容旧版本。
     */
    @Deprecated
    void markPaid(Long orderId, String paymentNo);
}
