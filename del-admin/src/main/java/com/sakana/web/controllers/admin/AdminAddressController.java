package com.sakana.web.controllers.admin;

import com.sakana.feign.UserFeignClient;
import com.sakana.web.vo.AdminAddressPageResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 收货地址管理
 */
@RestController
@RequestMapping("/api/v1/admin/addresses")
@RequiredArgsConstructor
@Tag(name = "管理后台-收货地址", description = "地址查询、强制删除")
public class AdminAddressController {

    private final UserFeignClient userFeignClient;

    @GetMapping
    @Operation(summary = "收货地址分页（按用户ID/姓名/手机号筛选）")
    public R<AdminAddressPageResp> getPage(
            @Parameter(description = "用户ID（精确）") @RequestParam(required = false) Long userId,
            @Parameter(description = "关键词：姓名/手机号（模糊）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return userFeignClient.getAddressPage(userId, keyword, page, size);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "强制删除收货地址")
    public R<Void> delete(@PathVariable Long id) {
        return userFeignClient.deleteAddress(id);
    }
}
