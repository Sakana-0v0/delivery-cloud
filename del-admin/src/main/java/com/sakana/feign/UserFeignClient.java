package com.sakana.feign;

import com.sakana.dto.request.admin.AdminUserListQuery;
import com.sakana.dto.request.admin.UserStatusReq;
import com.sakana.web.vo.AdminAddressPageResp;
import com.sakana.web.vo.AdminUserPageResp;
import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 用户服务 Feign 客户端（供 del-admin 调用 del-user）
 */
@FeignClient(name = "del-user", contextId = "adminUserFeignClient")
public interface UserFeignClient {

    // ==================== 用户管理 ====================

    @GetMapping("/internal/admin/users/")
    R<AdminUserPageResp> getUserPage(AdminUserListQuery query);

    @GetMapping("/internal/admin/users/{id}")
    R<Object> getUserDetail(@PathVariable("id") Long id);

    @PutMapping("/internal/admin/users/{id}/status")
    R<Void> changeUserStatus(@PathVariable("id") Long id, @RequestBody UserStatusReq req);

    // ==================== 收货地址管理 ====================

    @GetMapping("/internal/admin/addresses")
    R<AdminAddressPageResp> getAddressPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size);

    @DeleteMapping("/internal/admin/addresses/{id}")
    R<Void> deleteAddress(@PathVariable("id") Long id);
}
