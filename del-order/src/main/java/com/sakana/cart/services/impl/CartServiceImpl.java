package com.sakana.cart.services.impl;

import com.sakana.cart.enums.CartErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.feign.ProductFeignClient;
import com.sakana.feign.vo.ProductSnapshotVO;
import com.sakana.cart.services.CartService;
import com.sakana.cart.web.vo.CartItemVO;
import com.sakana.cart.web.vo.CartVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 购物车服务实现
 *
 * <p>主存 Redis Hash：Key: del-cart:user:{userId}，field=productId，value=quantity。
 * 商品信息通过 Feign 调用 del-product 服务实时获取。
 */
@Slf4j
@Service
public class CartServiceImpl implements CartService {

    /** 缓存 Key 前缀 */
    private static final String CACHE_KEY_PREFIX = "del-cart:user:";
    /** 30 天过期 */
    private static final long CACHE_TTL_DAYS = 30L;

    /**
     * 购物车专用 RedisTemplate
     * 使用 @Qualifier("cartRedisTemplate") 注入，与公共 RedisConfig 区分
     */
    private final RedisTemplate<String, Object> redisTemplate;

    private final ProductFeignClient productFeignClient;

    /**
     * 构造器注入，使用 @Qualifier 区分同名 Bean
     */
    public CartServiceImpl(
            @Qualifier("cartRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            ProductFeignClient productFeignClient) {
        this.redisTemplate = redisTemplate;
        this.productFeignClient = productFeignClient;
    }

    // ==================== 查询 ====================

    @Override
    public CartVO getCart(Long userId) {
        String cacheKey = getCacheKey(userId);

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
        if (entries == null || entries.isEmpty()) {
            return emptyCart();
        }

        List<CartItemVO> items = new ArrayList<>(entries.size());
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long productId = Long.parseLong(entry.getKey().toString());
            Integer quantity = Integer.parseInt(entry.getValue().toString());

            ProductSnapshotVO product = safeGetProduct(productId);
            if (product == null) {
                // 商品不存在 / 已下架 / 已删除 / 服务不可用 → 跳过该条目
                continue;
            }

            // 单价以 realPrice 为准
            BigDecimal price = product.getRealPrice();
            if (price == null) {
                continue;
            }
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));

            CartItemVO item = new CartItemVO();
            item.setProductId(productId);
            item.setName(product.getName());
            item.setCover(product.getCover());
            item.setPrice(price);
            item.setQuantity(quantity);
            item.setSubtotal(subtotal);
            items.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        CartVO cart = new CartVO();
        cart.setItems(items);
        cart.setTotalAmount(totalAmount);
        cart.setItemCount(items.size());
        return cart;
    }

    // ==================== 写入 ====================

    @Override
    public void addItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BizException(CartErrorCode.CART_ITEM_INVALID);
        }

        ProductSnapshotVO product = requireAvailableProduct(productId);
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new BizException(CartErrorCode.CART_ITEM_INVALID, "库存不足");
        }

        String cacheKey = getCacheKey(userId);
        // HINCRBY：若 field 不存在则创建；存在则累加
        redisTemplate.opsForHash().increment(cacheKey, productId.toString(), quantity);
        redisTemplate.expire(cacheKey, CACHE_TTL_DAYS, TimeUnit.DAYS);

        log.debug("[购物车] 加购: userId={}, productId={}, quantity={}", userId, productId, quantity);
    }

    @Override
    public void updateQuantity(Long userId, Long productId, int quantity) {
        String cacheKey = getCacheKey(userId);

        if (quantity <= 0) {
            // 数量 <=0 直接删除
            redisTemplate.opsForHash().delete(cacheKey, productId.toString());
            log.debug("[购物车] 更新数量<=0 删除: userId={}, productId={}", userId, productId);
            return;
        }

        ProductSnapshotVO product = requireAvailableProduct(productId);
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new BizException(CartErrorCode.CART_ITEM_INVALID, "库存不足");
        }

        redisTemplate.opsForHash().put(cacheKey, productId.toString(), quantity);
        redisTemplate.expire(cacheKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
        log.debug("[购物车] 更新数量: userId={}, productId={}, quantity={}", userId, productId, quantity);
    }

    @Override
    public void removeItem(Long userId, Long productId) {
        String cacheKey = getCacheKey(userId);
        redisTemplate.opsForHash().delete(cacheKey, productId.toString());
        log.debug("[购物车] 删除商品: userId={}, productId={}", userId, productId);
    }

    @Override
    public void clearCart(Long userId) {
        String cacheKey = getCacheKey(userId);
        redisTemplate.delete(cacheKey);
        log.info("[购物车] 清空: userId={}", userId);
    }

    // ==================== 私有 ====================

    private String getCacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }

    private CartVO emptyCart() {
        CartVO cart = new CartVO();
        cart.setItems(new ArrayList<>());
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setItemCount(0);
        return cart;
    }

    /**
     * 强校验：必须能成功获取商品（且商品本身存在的状态）。
     * 找不到 / 已下架 / 已删除 抛 404；服务降级抛 503。
     */
    private ProductSnapshotVO requireAvailableProduct(Long productId) {
        try {
            return productFeignClient.getProductSnapshot(productId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[购物车] 商品服务异常 productId={}", productId, e);
            throw new BizException(9301, "商品服务暂不可用，请稍后重试", 503);
        }
    }

    /**
     * 弱校验：仅在 getCart 时使用，失败时返回 null 让上层跳过该项。
     */
    private ProductSnapshotVO safeGetProduct(Long productId) {
        try {
            return productFeignClient.getProductSnapshot(productId);
        } catch (Exception e) {
            log.debug("[购物车] 跳过不可用商品 productId={}, msg={}", productId, e.getMessage());
            return null;
        }
    }
}
