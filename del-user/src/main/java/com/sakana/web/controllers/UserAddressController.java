package com.sakana.web.controllers;

import com.sakana.security.SecurityUtil;
import com.sakana.services.UserAddressService;
import com.sakana.web.vo.R;
import com.sakana.web.vo.UserAddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端 - 收货地址
 */
@RestController
@RequestMapping("/api/v1/user/addresses")
@RequiredArgsConstructor
@Tag(name = "收货地址", description = "我的收货地址 CRUD")
public class UserAddressController {

    private final UserAddressService userAddressService;

    @GetMapping
    @Operation(summary = "我的收货地址列表")
    public R<List<UserAddressVO>> list() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userAddressService.listByUser(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个地址")
    public R<UserAddressVO> get(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userAddressService.getByUserAndId(userId, id));
    }

    @PostMapping
    @Operation(summary = "新增地址")
    public R<Long> create(@RequestBody UserAddressVO req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userAddressService.createAddress(userId, req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改地址")
    public R<Void> update(@PathVariable Long id, @RequestBody UserAddressVO req) {
        Long userId = SecurityUtil.getCurrentUserId();
        userAddressService.updateAddress(userId, id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除地址")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        userAddressService.deleteAddress(userId, id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认地址")
    public R<Void> setDefault(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        userAddressService.setDefault(userId, id);
        return R.ok();
    }
}
