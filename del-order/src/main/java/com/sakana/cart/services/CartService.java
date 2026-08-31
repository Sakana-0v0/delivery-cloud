package com.sakana.cart.services;

import com.sakana.cart.web.vo.CartVO;

/**
 * 购物车服务接口
 *
 * <p>主存 Redis Hash，Key: del-cart:user:{userId}<br>
 * field: productId，value: quantity
 */
public interface CartService {

    /**
     * 获取用户购物车（含商品快照）
     */
    CartVO getCart(Long userId);

    /**
     * 添加商品到购物车
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param quantity  数量（>0）
     */
    void addItem(Long userId, Long productId, int quantity);

    /**
     * 修改购物车商品数量
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param quantity  数量，<=0 表示删除
     */
    void updateQuantity(Long userId, Long productId, int quantity);

    /**
     * 删除购物车商品
     */
    void removeItem(Long userId, Long productId);

    /**
     * 清空购物车
     */
    void clearCart(Long userId);
}
