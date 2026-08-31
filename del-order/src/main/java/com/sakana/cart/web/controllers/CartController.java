package com.sakana.cart.web.controllers;

import com.sakana.cart.dto.request.AddItemReq;
import com.sakana.cart.dto.request.UpdateQuantityReq;
import com.sakana.security.SecurityUtil;
import com.sakana.cart.services.CartService;
import com.sakana.cart.web.vo.CartVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（C 端）
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "购物车", description = "购物车CRUD：查看、加购、改数量、删商品、清空")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "查看购物车")
    public R<CartVO> getCart() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    @Operation(summary = "添加商品到购物车")
    public R<Void> addItem(@Valid @RequestBody AddItemReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.addItem(userId, req.getProductId(), req.getQuantity());
        return R.ok();
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "修改购物车商品数量（数量<=0 等同删除）")
    public R<Void> updateQuantity(@PathVariable Long productId,
                                  @Valid @RequestBody UpdateQuantityReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.updateQuantity(userId, productId, req.getQuantity());
        return R.ok();
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "删除购物车商品")
    public R<Void> removeItem(@PathVariable Long productId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.removeItem(userId, productId);
        return R.ok();
    }

    @DeleteMapping
    @Operation(summary = "清空购物车")
    public R<Void> clearCart() {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.clearCart(userId);
        return R.ok();
    }
}
