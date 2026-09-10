package com.sakana.cs.web;

import com.sakana.cs.context.AuthContext;
import com.sakana.cs.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/cs")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }

        SseEmitter emitter = new SseEmitter(60_000L);
        String userId = AuthContext.getUserId();
        log.info("[ChatController] 收到消息: userId={}, message={}", userId, message);

        // ★ BUG-019 修复：AuthContext 清理推迟到 SSE 结束后（不再在 Filter finally 中清理）
        emitter.onCompletion(() -> {
            log.debug("[ChatController] SSE 完成，清理 AuthContext");
            AuthContext.clear();
        });
        emitter.onTimeout(() -> {
            log.debug("[ChatController] SSE 超时，清理 AuthContext");
            AuthContext.clear();
        });
        emitter.onError(e -> {
            log.debug("[ChatController] SSE 异常，清理 AuthContext: {}", e.getMessage());
            AuthContext.clear();
        });

        chatService.streamChat(
            userId,
            message,
            chunk -> {
                try {
                    // BUG-015: SseEmitter.send() 自动加 "data: " 前缀
                    emitter.send(chunk);
                } catch (IOException e) {
                    log.warn("[ChatController] SSE send 失败: {}", e.getMessage());
                }
            },
            v -> {
                try {
                    emitter.send("{\"type\":\"done\",\"content\":\"\"}");
                    emitter.complete();
                } catch (IOException e) {
                    log.warn("[ChatController] SSE complete 失败: {}", e.getMessage());
                }
            },
            error -> {
                log.error("[ChatController] 聊天异常: {}", error.getMessage(), error);
                try {
                    emitter.send("{\"type\":\"error\",\"content\":\"" + error.getMessage() + "\"}");
                } catch (IOException e) {
                    log.warn("[ChatController] SSE error 发送失败: {}", e.getMessage());
                }
                emitter.completeWithError(error);
            }
        );

        return emitter;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "del-cs", "mode", "langchain4j");
    }
}
