package com.sakana.cs.config;

import com.sakana.cs.memory.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * langchain4j ChatMemory 配置（#AI-CS-002-PHASE2）
 */
@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    private final RedisChatMemoryStore chatMemoryStore;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // 每个 memoryId 一个独立的 MessageWindowChatMemory
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(20)
                .id(memoryId.toString())
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
