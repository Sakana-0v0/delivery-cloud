package com.sakana.web.controllers.internal;

import com.sakana.services.UserAddressService;
import com.sakana.web.vo.AdminAddressPageResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 收货地址内部接口（供 del-admin 调用）
 */
@RestController
@RequestMapping("/internal/admin/addresses")
@RequiredArgsConstructor
public class InternalAddressController {

    private final UserAddressService userAddressService;

    @GetMapping
    @Operation(summary = "收货地址分页（按用户ID/姓名/手机号筛选）")
    public R<AdminAddressPageResp> getPage(
            @Parameter(description = "用户ID（精确）") @RequestParam(required = false) Long userId,
            @Parameter(description = "关键词：姓名/手机号（模糊）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return R.ok(userAddressService.adminGetPage(userId, keyword, page, size));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "强制删除收货地址")
    public R<Void> delete(@PathVariable Long id) {
        userAddressService.adminDeleteAddress(id);
        return R.ok();
    }
}
