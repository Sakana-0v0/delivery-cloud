package com.sakana.web.controllers.admin;

import com.sakana.admin.security.AdminSecurityUtil;
import com.sakana.dto.request.admin.AdminUserListQuery;
import com.sakana.dto.request.admin.UserStatusReq;
import com.sakana.feign.UserFeignClient;
import com.sakana.web.vo.AdminUserPageResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 用户管理
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "管理后台-用户", description = "用户列表、详情、冻结/解冻")
public class AdminUserController {

    private final UserFeignClient userFeignClient;

    @GetMapping
    @Operation(summary = "用户列表（多条件分页：keyword/status）")
    public R<AdminUserPageResp> getPage(@ModelAttribute AdminUserListQuery query) {
        return userFeignClient.getUserPage(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public R<Object> getDetail(@PathVariable Long id) {
        return userFeignClient.getUserDetail(id);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "冻结/解冻 {status:0正常 1冻结}")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusReq req) {
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        String adminName = AdminSecurityUtil.getCurrentUsername();
        return userFeignClient.changeUserStatus(id, req);
    }
}
