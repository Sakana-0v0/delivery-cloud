package com.sakana.consumers;

import com.sakana.services.MessageService;
import com.sakana.web.vo.MessageVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部消息写入接口（其他服务直接 HTTP 调用，非 MQ 场景的兜底）
 *
 * <p>调用方：del-order / del-payment 等（简单事件）
 */
@Slf4j
@RestController
@RequestMapping("/internal/messages")
@RequiredArgsConstructor
@Tag(name = "消息-内部", description = "供其他服务直接调用写入站内消息")
public class InternalMessageController {

    private final MessageService messageService;

    /**
     * 写入一条站内消息
     *
     * @param body { userId, type, title, content, bizId }
     */
    @PostMapping
    public R<MessageVO> create(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String type = (String) body.get("type");
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String bizId = body.get("bizId") == null ? null : body.get("bizId").toString();

        log.info("[内部调用] 写入消息: userId={}, type={}, title={}", userId, type, title);
        return R.ok(messageService.saveMessage(userId, type, title, content, bizId));
    }
}
