package com.sakana.cs.web;

import com.sakana.cs.context.AuthContext;
import com.sakana.cs.context.ChatContext;
import com.sakana.cs.context.ChatContextHolder;
import com.sakana.cs.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE 聊天入口。
 *
 * <p>★ P0-UserAuth重构：在 SSE 入口处建立 ChatContext（userId + token），
 * 供 LangChain4j 异步 Tool 调用链路使用；userId 不再嵌入 UserMessage 文本，
 * 由 ChatContextHolder 透传到工具层，避免 LLM 误读/注入导致越权。
 *
 * <p>SSE 完成/超时/异常时统一清理 ChatContextHolder 与 AuthContext。
 */
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
        String token = AuthContext.getToken();
        log.info("[ChatController] 收到消息: userId={}, message={}", userId, message);

        // ★ P0-UserAuth重构：在 SSE 入口处建立 ChatContext，工具层不再依赖消息文本里的 userId
        ChatContextHolder.set(new ChatContext(userId, token, System.currentTimeMillis()));

        Runnable cleanup = () -> {
            log.debug("[ChatController] SSE 结束，清理 ChatContext/AuthContext");
            ChatContextHolder.clear();
            AuthContext.clear();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> {
            log.debug("[ChatController] SSE 异常，清理上下文: {}", e.getMessage());
            cleanup.run();
        });

        chatService.streamChat(
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