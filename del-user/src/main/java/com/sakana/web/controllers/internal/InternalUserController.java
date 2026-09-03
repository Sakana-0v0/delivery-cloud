package com.sakana.web.controllers.internal;

import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.dto.request.admin.AdminUserListQuery;
import com.sakana.dto.request.admin.UserStatusReq;
import com.sakana.services.UserService;
import com.sakana.web.vo.AdminUserPageResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 用户内部接口（供 del-admin 调用）
 */
@RestController
@RequestMapping("/internal/admin/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * 用户分页（多条件：keyword/status）
     */
    @GetMapping
    @Operation(summary = "用户列表（内部）")
    public R<AdminUserPageResp> getPage(AdminUserListQuery query) {
        return R.ok(userService.adminGetPage(query));
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "用户详情（内部）")
    public R<User> getDetail(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        return R.ok(user);
    }

    /**
     * 冻结/解冻用户
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "改变用户状态（内部）")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody UserStatusReq req) {
        userService.adminChangeStatus(id, req, -1L, "internal-admin-call");
        return R.ok();
    }
}
