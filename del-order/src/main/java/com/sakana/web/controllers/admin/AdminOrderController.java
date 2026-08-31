package com.sakana.web.controllers.admin;

import com.sakana.dto.request.admin.AdminOrderListQuery;
import com.sakana.dto.request.admin.AdminOrderStatusReq;
import com.sakana.security.SecurityUtil;
import com.sakana.services.OrderService;
import com.sakana.web.vo.AdminOrderPageResp;
import com.sakana.web.vo.AdminOrderVO;
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
 * 管理后台 - 订单管理（位于 del-order 内部，复用 OrderService）
 *
 * <p>路径前缀：/api/v1/admin/orders/**
 * <p>权限：ADMIN / SUPER_ADMIN（由网关 + 本服务 SecurityConfig 双重校验）
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "管理后台-订单", description = "订单查询、状态修改")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "订单分页查询",
            description = "支持 status / userId / orderNo 模糊 / startTime / endTime 过滤")
    public R<AdminOrderPageResp> getPage(@ModelAttribute AdminOrderListQuery query) {
        return R.ok(orderService.adminGetPage(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情（带订单项）")
    public R<AdminOrderVO> getDetail(@PathVariable Long id) {
        return R.ok(orderService.adminGetDetail(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改订单状态（状态机校验）")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody AdminOrderStatusReq req) {
        Long adminId = SecurityUtil.getCurrentUserId();
        String adminName = SecurityUtil.getCurrentUsername();
        orderService.adminChangeStatus(id, req, adminId, adminName);
        return R.ok();
    }
}
