package com.sakana.cs.config;

import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * langchain4j + DashScope Qwen 流式模型配置（#AI-CS-002-PHASE2 + #BUG-015）
 */
@Slf4j
@Configuration
public class ChatAutoConfig {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.model-name:qwen-max}")
    private String modelName;

    @Bean
    public QwenStreamingChatModel qwenStreamingChatModel() {
        log.info("[ChatAutoConfig] 初始化 QwenStreamingChatModel, model={}", modelName);
        return QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                // ★ BUG-017: 降低 LLM 创造性，减少幻觉
                .temperature(0.1F)
                .topP(0.7)
                .build();
    }
}
