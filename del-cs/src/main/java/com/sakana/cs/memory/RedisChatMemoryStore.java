package com.sakana.cs.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "cs:chat:messages:";
    private static final Duration TTL = Duration.ofMinutes(30);

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String key = keyOf(memoryId);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            // 使用 langchain4j 内置 JSON 序列化
            List<ChatMessage> messages = dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson(json);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            log.warn("[RedisChatMemoryStore] getMessages 失败: memoryId={}, error={}", memoryId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String key = keyOf(memoryId);
            // 使用 langchain4j 内置 JSON 序列化
            String json = dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("[RedisChatMemoryStore] 更新消息: memoryId={}, count={}", memoryId, messages.size());
        } catch (Exception e) {
            log.warn("[RedisChatMemoryStore] updateMessages 失败: memoryId={}, error={}", memoryId, e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            String key = keyOf(memoryId);
            redisTemplate.delete(key);
            log.debug("[RedisChatMemoryStore] 删除消息: memoryId={}", memoryId);
        } catch (Exception e) {
            log.warn("[RedisChatMemoryStore] deleteMessages 失败: memoryId={}, error={}", memoryId, e.getMessage());
        }
    }

    private String keyOf(Object memoryId) {
        return KEY_PREFIX + memoryId.toString();
    }
}