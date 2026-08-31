package com.sakana.admin.web.controllers;

import com.sakana.admin.dto.request.AdminLoginReq;
import com.sakana.admin.security.AdminSecurityUtil;
import com.sakana.admin.security.AdminTokenBlacklist;
import com.sakana.admin.services.AdminService;
import com.sakana.admin.web.vo.AdminLoginResp;
import com.sakana.utils.JwtUtil;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员认证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "管理后台-认证", description = "管理员登录、当前信息、登出")
public class AdminAuthController {

    private final AdminService adminService;
    private final JwtUtil jwtUtil;
    private final AdminTokenBlacklist tokenBlacklist;

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public R<AdminLoginResp> login(@Valid @RequestBody AdminLoginReq req) {
        return R.ok(adminService.login(req));
    }

    @GetMapping("/me")
    @Operation(summary = "当前管理员信息")
    public R<AdminLoginResp.UserInfo> me() {
        Long adminId = AdminSecurityUtil.getCurrentAdminId();
        return R.ok(adminService.me(adminId));
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员登出")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtUtil.getJti(token);
                long ttl = jwtUtil.getExpirationSeconds(token);
                Long adminId = jwtUtil.getUserId(token);
                if (ttl > 0) {
                    adminService.logout(jti, ttl);
                }
                tokenBlacklist.unregisterUserJti(adminId, jti);
                log.info("[管理员登出] token已加入黑名单: jti={}, adminId={}", jti, adminId);
            } catch (Exception e) {
                log.warn("[管理员登出] token解析失败", e);
            }
        }
        return R.ok();
    }
}
