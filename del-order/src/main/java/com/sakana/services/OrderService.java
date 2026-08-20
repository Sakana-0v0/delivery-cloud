package com.sakana.services;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.dao.entity.Order;
import com.sakana.dto.request.OrderCreateReq;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.OrderVO;

/**
 * 订单服务接口
 */
public interface OrderService extends IService<Order> {

    /**
     * 创建订单（C 端）
     */
    OrderVO createOrder(Long userId, OrderCreateReq req);

    /**
     * 我的订单列表（C 端）
     *
     * @param status null=全部
     */
    OrderPageResp getMyOrders(Long userId, Integer status, Integer page, Integer size);

    /**
     * 订单详情（C 端，含订单项）
     */
    OrderVO getOrderDetail(Long userId, Long orderId);

    /**
     * 取消订单（仅待支付可取消）
     */
    void cancelOrder(Long userId, Long orderId);

    /**
     * 确认收货（仅配送中可确认）
     */
    void confirmOrder(Long userId, Long orderId);
}
