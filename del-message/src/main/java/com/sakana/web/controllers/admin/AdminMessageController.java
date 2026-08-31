package com.sakana.web.controllers.admin;

import com.sakana.services.MessageService;
import com.sakana.web.vo.MessageVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 站内消息查看
 */
@RestController
@RequestMapping("/api/v1/admin/messages")
@RequiredArgsConstructor
@Tag(name = "管理后台-消息", description = "全量站内消息查看（用于排查 / 客服）")
public class AdminMessageController {

    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "全量消息分页（按用户/类型/已读状态筛选）")
    public R<List<MessageVO>> list(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "消息类型 order/pay/refund/system") @RequestParam(required = false) String type,
            @Parameter(description = "是否已读 0/1") @RequestParam(required = false) Integer isRead,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return R.ok(messageService.adminListAll(userId, type, isRead, page, size));
    }
}
