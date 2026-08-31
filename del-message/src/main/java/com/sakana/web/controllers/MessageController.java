package com.sakana.web.controllers;

import com.sakana.security.SecurityUtil;
import com.sakana.services.MessageService;
import com.sakana.web.vo.MessagePageResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息接口（C 端）
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "消息", description = "我的消息、已读、未读数")
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "我的消息（分页）")
    public R<MessagePageResp> list(
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(messageService.listByUser(userId, isRead, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读消息数")
    public R<Map<String, Long>> unreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(Map.of("count", messageService.getUnreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记已读")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        messageService.markRead(userId, id);
        return R.ok();
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部已读")
    public R<Void> markAllRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        messageService.markAllRead(userId);
        return R.ok();
    }
}
