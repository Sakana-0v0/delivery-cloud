package com.sakana.cs.service;

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
     * userId 嵌入到消息中，让工具通过 userId 参数获取，
     * 解决 SSE 异步执行时 ThreadLocal 丢失的问题。
     */
    public void streamChat(String userId, String userMessage,
                          Consumer<String> onChunk,
                          Consumer<Void> onComplete,
                          Consumer<Throwable> onError) {
        if (assistant == null) {
            onError.accept(new IllegalStateException("Assistant 未初始化"));
            return;
        }
        // ★ BUG-019 修复：在消息中嵌入 userId，让 LLM 传给需要用户ID的工具
        String enrichedMessage = "【当前用户ID: " + userId + "】" + userMessage;
        log.info("[ChatService] streamChat userId={}, enrichedMessage={}", userId, enrichedMessage);
        try {
            TokenStream stream = assistant.chat(userId, enrichedMessage);
            stream
                .onPartialResponse(token -> {
                    onChunk.accept(formatSSE("token", token));
                })
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
