package com.sakana.cs.service;

import com.sakana.cs.context.ChatContextHolder;
import com.sakana.cs.prompt.PromptManager;
import com.sakana.cs.service.tools.OrderDetailTool;
import com.sakana.cs.service.tools.OrderHistoryTool;
import com.sakana.cs.service.tools.SearchDishesTool;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.function.Consumer;

/**
 * Chat 业务编排（基于 LangChain4j 流式 Agent）。
 *
 * <p>★ P0-UserAuth重构：
 * <ul>
 *   <li>userId 不再嵌入 UserMessage 文本</li>
 *   <li>userId 通过 ChatContextHolder 透传到 Tool 层</li>
 *   <li>memoryId 仍使用 userId，保持按用户隔离会话记忆</li>
 * </ul>
 */
@Slf4j
@Service
public class ChatService {

    private final QwenStreamingChatModel streamingModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final PromptManager promptManager;
    private final SearchDishesTool searchDishesTool;
    private final OrderDetailTool orderDetailTool;
    private final OrderHistoryTool orderHistoryTool;

    private Assistant assistant;

    public ChatService(
            QwenStreamingChatModel streamingModel,
            ChatMemoryProvider chatMemoryProvider,
            PromptManager promptManager,
            SearchDishesTool searchDishesTool,
            OrderDetailTool orderDetailTool,
            OrderHistoryTool orderHistoryTool) {
        this.streamingModel = streamingModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.promptManager = promptManager;
        this.searchDishesTool = searchDishesTool;
        this.orderDetailTool = orderDetailTool;
        this.orderHistoryTool = orderHistoryTool;
    }

    @PostConstruct
    public void init() {
        log.info("[ChatService] 初始化 langchain4j Assistant (真流式)...");
        String systemPrompt = promptManager.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是外卖系统智能客服小饿，请简洁友好地回复用户。";
        }
        log.info("[ChatService] systemPrompt length={}", systemPrompt.length());

        this.assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessage(systemPrompt)
                .tools(searchDishesTool, orderDetailTool, orderHistoryTool)
                .build();

        log.info("[ChatService] Assistant 初始化完成 (真流式)");
    }

    /**
     * ★ P0-UserAuth重构：userId 不再作为方法参数传入，也不再嵌入 UserMessage 文本。
     *
     * @param userMessage 用户原始消息（不含 userId 前缀）
     */
    public void streamChat(String userMessage,
                          Consumer<String> onChunk,
                          Consumer<Void> onComplete,
                          Consumer<Throwable> onError) {
        if (assistant == null) {
            onError.accept(new IllegalStateException("Assistant 未初始化"));
            return;
        }

        // ★ P0-UserAuth重构：从 ChatContextHolder 取 userId 作为 memoryId
        String userId = ChatContextHolder.getUserId();
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            log.error("[ChatService] ChatContextHolder 未设置 userId，拒绝响应");
            onError.accept(new IllegalStateException("用户身份未建立，请重新登录"));
            return;
        }

        log.info("[ChatService] streamChat userId={}, message={}", userId, userMessage);
        try {
            TokenStream stream = assistant.chat(userId, userMessage);
            stream
                .onPartialResponse(token -> onChunk.accept(formatSSE("token", token)))
                .onToolExecuted(toolExecution -> {
                    log.info("[ChatService] Tool 执行: name={}", toolExecution.request().name());
                    onChunk.accept(formatSSE("tool", toolExecution.toString()));
                })
                .onCompleteResponse(response -> {
                    log.info("[ChatService] LLM 回复完成");
                    onComplete.accept(null);
                })
                .onError(error -> {
                    log.error("[ChatService] LLM 异常", error);
                    onError.accept(error);
                })
                .start();
        } catch (Exception e) {
            log.error("[ChatService] streamChat 异常", e);
            onError.accept(e);
        }
    }

    private String formatSSE(String type, String content) {
        if (content == null) content = "";
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"type\":\"" + type + "\",\"content\":\"" + escaped + "\"}";
    }

    interface Assistant {
        TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }
}